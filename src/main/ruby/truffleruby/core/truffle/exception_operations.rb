# frozen_string_literal: true

# Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module ExceptionOperations
    def self.class_name(receiver)
      Truffle::Type.object_class(receiver).name
    end

    # MRI: name_err_mesg_to_str
    def self.receiver_string(exception)
      receiver = exception.receiver
      ret = begin
        if Primitive.object_respond_to?(receiver, :inspect, false)
          Truffle::Type.rb_inspect(receiver)
        else
          nil
        end
      rescue Exception # rubocop:disable Lint/RescueException
        nil
      end
      ret = Truffle::Type.rb_any_to_s(receiver) unless ret && ret.bytesize <= 65
      if ret.start_with?('#')
        ret
      else
        "#{ret}:#{class_name(receiver)}"
      end
    end

    def self.message_and_class(exception, highlight)
      message = exception.message.to_s
      if highlight
        if i = message.index("\n")
          "\e[1m#{message[0...i]} (\e[1;4m#{exception.class}\e[m\e[1m)\e[0m#{message[i..-1]}"
        else
          "\e[1m#{message} (\e[1;4m#{exception.class}\e[m\e[1m)\e[0m"
        end
      else
        if i = message.index("\n")
          "#{message[0...i]} (#{exception.class})#{message[i..-1]}"
        else
          "#{message} (#{exception.class})"
        end
      end
    end

    def self.backtrace_message(highlight, reverse, bt, exc)
      message = Truffle::ExceptionOperations.message_and_class(exc, highlight)
      message = message.end_with?("\n") ? message : "#{message}\n"
      return '' if bt.nil? || bt.empty?
      if reverse
        bt[1..-1].reverse.map do |l|
          "\tfrom #{l}\n"
        end.join + "#{bt[0]}: #{message}"
      else
        "#{bt[0]}: #{message}" + bt[1..-1].map do |l|
          "\tfrom #{l}\n"
        end.join
      end
    end

    def self.append_causes(str, err, causes, reverse, highlight)
      if !err.cause.nil? && Exception === err.cause && !causes.has_key?(err.cause)
        causes[err.cause] = true
        if reverse
          append_causes(str, err.cause, causes, reverse, highlight)
          str << Truffle::ExceptionOperations.backtrace_message(highlight, reverse, err.cause.backtrace, err.cause)
        else
          str << Truffle::ExceptionOperations.backtrace_message(highlight, reverse, err.cause.backtrace, err.cause)
          append_causes(str, err.cause, causes, reverse, highlight)
        end
      end
    end

    IMPLICIT_CONVERSION_METHODS = [:to_int, :to_ary, :to_str, :to_sym, :to_hash, :to_proc, :to_io]

    def self.conversion_error_message(meth, obj, cls)
      message = IMPLICIT_CONVERSION_METHODS.include?(meth) ? 'no implicit conversion of' : "can't convert"
      type_name = to_class_name(obj)
      "#{message} #{type_name} into #{cls}"
    end

    def self.to_class_name(val)
      case val
      when nil
        'nil'
      when true
        'true'
      when false
        'false'
      else
        Truffle::Type.object_class(val).name
      end
    end

    def self.comparison_error_message(x, y)
      y_classname = if Truffle::Type.is_special_const?(y)
                      y.inspect
                    else
                      y.class
                    end
      "comparison of #{x.class} with #{y_classname} failed"
    end

    NO_METHOD_ERROR = Proc.new do |exception|
      format("undefined method `%s' for %s", exception.name, receiver_string(exception))
    end

    NO_LOCAL_VARIABLE_OR_METHOD_ERROR = Proc.new do |exception|
      format("undefined local variable or method `%s' for %s", exception.name, receiver_string(exception))
    end

    PRIVATE_METHOD_ERROR = Proc.new do |exception|
      format("private method `%s' called for %s", exception.name, receiver_string(exception))
    end

    PROTECTED_METHOD_ERROR = Proc.new do |exception|
      format("protected method `%s' called for %s", exception.name, receiver_string(exception))
    end

    SUPER_METHOD_ERROR = Proc.new do |exception|
      format("super: no superclass method `%s'", exception.name)
    end

    def self.format_errno_error_message(errno_description, errno, extra_message)
      if errno_description.nil?
        "unknown error: #{errno} - #{extra_message}"
      else
        "#{errno_description}#{extra_message}"
      end
    end
  end
end
