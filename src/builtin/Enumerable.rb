#
# Copyright (C) 2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
#
# JRuby - http://jruby.sourceforge.net
#
# This file is part of JRuby
#
# JRuby is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License as
# published by the Free Software Foundation; either version 2 of the
# License, or (at your option) any later version.
#
# JRuby is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public
# License along with JRuby; if not, write to
# the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
# Boston, MA  02111-1307 USA

# The Enumerable mixin provides collection classes with several traversal and
# searching methods, and with the ability to sort. The class must provide a
# method each, which yields successive members of the collection. If
# Enumerable#max, #min, or #sort is used, the objects in the collection must
# also implement a meaningful <=> operator, as these methods rely on an
# ordering between members of the collection.
module Enumerable
  
  def to_a
    result = []
    each do |item|
      result << item
    end
    result
  end
  alias entries to_a

  def sort (&proc)
    to_a.sort &proc
  end

  def sort_by
    result = []
    each do |item|
      result << [yield(item), item]
    end
    result.sort! do |a, b|
      a.first <=> b.first
    end
    result.collect do |item|
      item.last
    end
  end

  def grep (pattern)
    result = []
    each do |item|
      if block_given? then
	result << yield(item) if pattern === item
      else
	result << item if pattern === item
      end
    end
    result
  end
  
  def detect (nothing_found = nil)
    result = nil
    each { |element| (result = element) if (result == nil and yield(element)) } 
    (result = nothing_found.call) unless (result != nil or nothing_found.nil?)
    result
  end
  alias find detect

  def select
    result = []
    each do |item|
      result << item if yield(item)
    end
    result
  end
  alias find_all select

  def reject
    result = []
    each do |item|
      result << item unless yield(item)
    end
    result
  end

  def collect
    result = []
    each do |item|
      result << yield(item)
    end
    result
  end
  alias map collect

  def inject (*args)
    raise ArgumentError, "wrong number of arguments (#{args.length} for 1)" if args.length > 1
    if args.length == 1 then
      result = args[0]
      first = false
    else
      result = nil
      first = true
    end
    each do |item|
      if first then
	first = false
	result = item
      else
        result = yield(result, item)
      end
    end
    result
  end

  def partition
    result = [[], []]
    each do |item|
      result[yield(item) ? 0 : 1] << item
    end
    result
  end

  def each_with_index
    index = 0
    each do |item|
      yield(item, index)
      index += 1
    end
    self
  end

  def include? (value)
    result = false
    each { |item| result = true if item == value }
    result
  end
  alias member? include?

  def max
    if block_given? then
      cmp = lambda { |a, b| yield(a, b) > 0 }
    else
      cmp = lambda { |a, b| (a <=> b) > 0 }
    end
    result = nil
    each do |item|
      result = item if result.nil? || cmp.call(item, result)
    end
    result
  end

  def min
    if block_given? then
      cmp = lambda { |a, b| yield(a, b) < 0 }
    else
      cmp = lambda { |a, b| (a <=> b) < 0 }
    end
    result = nil
    each do |item|
      result = item if result.nil? || cmp.call(item, result)
    end
    result
  end

  def all?
    return all? {|obj| obj} unless block_given?

    result = true
    each { |item| result = false unless yield(item) }
    result
  end

  def any?
    return any? {|obj| obj} unless block_given?

    result = false
    each { |item| result = true if yield(item) }
    result
  end

  def zip(*args)
    zip = []
    i = 0
    each do |elem|
      array = [elem]
      args.each do |a| 
        array << a[i]
      end
      if block_given? then
        yield(array) 
      else 
        zip << array
      end
      i = i + 1
    end
    return nil if block_given?
    zip
  end

  # WARNING this isn't a default ruby method
  def group_by
    result = {}
    each do |item|
      (result[yield(item)] ||= []) << item
    end
    result
  end
end
