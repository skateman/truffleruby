#include <truffleruby-impl.h>
#include <internal.h>

// Parsing Ruby arguments from C functions

static VALUE rb_keyword_error_new(const char *error, VALUE keys) {
  long i = 0, len = RARRAY_LEN(keys);
  VALUE error_message = rb_sprintf("%s keyword%.*s", error, len > 1, "s");

  if (len > 0) {
    rb_str_append(error_message, rb_str_new_cstr(": "));
    while (1) {
      const VALUE k = RARRAY_AREF(keys, i);
      Check_Type(k, T_SYMBOL); /* wrong hash is given to rb_get_kwargs */
      rb_str_append(error_message, rb_sym2str(k));
      if (++i >= len) break;
      rb_str_append(error_message, rb_str_new_cstr(", "));
    }
  }

  return rb_exc_new_str(rb_eArgError, error_message);
}

NORETURN(static void rb_keyword_error(const char *error, VALUE keys)) {
  rb_exc_raise(rb_keyword_error_new(error, keys));
}

NORETURN(static void unknown_keyword_error(VALUE hash, const ID *table, int keywords)) {
  int i;
  for (i = 0; i < keywords; i++) {
    VALUE key = table[i];
    rb_hash_delete(hash, key);
  }
  rb_keyword_error("unknown", rb_hash_keys(hash));
}

static VALUE rb_tr_extract_keyword(VALUE keyword_hash, ID key, VALUE *values) {
   VALUE val = rb_hash_lookup2(keyword_hash, key, Qundef);
   if (values) {
     rb_hash_delete(keyword_hash, key);
   }
   return val;
}

int rb_get_kwargs(VALUE keyword_hash, const ID *table, int required, int optional, VALUE *values) {
  int rest = 0;
  int extracted = 0;
  VALUE missing = Qnil;

  if (optional < 0) {
    rest = 1;
    optional = -1-optional;
  }

  for (int n = 0; n < required; n++) {
    VALUE val = rb_tr_extract_keyword(keyword_hash, table[n], values);
    if (values) {
      values[n] = val;
    }
    if (val == Qundef) {
      if (NIL_P(missing)) {
        missing = rb_ary_new();
      }
      rb_ary_push(missing, table[n]);
      rb_keyword_error("missing", missing);
    }
    extracted++;
  }

  if (optional && !NIL_P(keyword_hash)) {
    for (int m = required; m < required + optional; m++) {
      VALUE val = rb_tr_extract_keyword(keyword_hash, table[m], values);
      if (values) {
        values[m] = val;
      }
      if (val != Qundef) {
        extracted++;
      }
    }
  }

  if (!rest && !NIL_P(keyword_hash)) {
    if (RHASH_SIZE(keyword_hash) > (unsigned int)(values ? 0 : extracted)) {
      unknown_keyword_error(keyword_hash, table, required + optional);
    }
  }

  for (int i = extracted; i < required + optional; i++) {
    values[i] = Qundef;
  }

  return extracted;
}
