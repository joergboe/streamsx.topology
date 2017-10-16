# coding=utf-8
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2016,2017
#
# Wrap the operator's iterable in a function
# that when called returns each value from
# the iteration returned by iter(callable).
# It the iteration returns None then that
# value is skipped (i.e. no tuple will be
# generated). When the iteration stops
# the wrapper function returns None.
#
def _splpy_iter_source(iterable) :
  it = iter(iterable)
  def _wf():
     try:
        while True:
            tv = next(it)
            if tv is not None:
                return tv
     except StopIteration:
       return None
  return _wf


# The decorated operators only support converting
# Python tuples or a list of Python tuples to
# an SPL output tuple. To simplify the generated code
# we handle any other type by using a wrapper function
# and converting to a Python tuple or list of Python
# tuples.
#
# A Python tuple returned by the wrapped function
# may be sparse, values not set by the dictionary
# (etc.) are set to None in the Python tuple.

def _splpy_convert_tuple(attributes):
    """Create a function that converts tuples to
    be submitted as dict objects into Python tuples
    with the value by position.
    Return function handles tuple,dict,list[tuple|dict|None],None
    """ 
    attr_count = len(attributes)
    attr_map = dict()
    for idx, name in enumerate(attributes):
        attr_map[name] = idx
    def _dict_to_tuple(tuple_):
        if isinstance(tuple_, dict):
            to_assign = set.intersection(set(tuple_.keys()), attributes) 
            tl = [None] * attr_count
            for name in to_assign:
                tl[attr_map[name]] = tuple_[name]
            return tuple(tl)
        return tuple_

    def _to_tuples(tuple_):
        if isinstance(tuple_, tuple):
            return tuple_
        if isinstance(tuple_, dict):
            return _dict_to_tuple(tuple_)
        if isinstance(tuple_, list):
            lt = list()
            for ev in tuple_:
                if isinstance(ev, dict):
                    ev = _dict_to_tuple(ev)
                lt.append(ev)
            return lt
        return tuple_
    return _to_tuples

def _splpy_to_tuples(fn, attributes):
   conv_fn = _splpy_convert_tuple(attributes)

   def _to_tuples(*args, **kwargs):
      value = fn(*args, **kwargs)
      return conv_fn(value)

   if hasattr(fn, '_splpy_shutdown'):
       def _splpy_shutdown():
           fn._splpy_shutdown()
       _to_tuples._splpy_shutdown = _splpy_shutdown
   return _to_tuples

def _splpy_release_memoryviews(*args):
    for o in args:
        if isinstance(o, memoryview):
            o.release()
        elif isinstance(o, list):
            for e in o:
                _splpy_release_memoryviews(e)
        elif isinstance(o, dict):
            for e in o.values():
                _splpy_release_memoryviews(e)

def _splpy_primitive_input_fns(obj):
    """Convert the list of class input functions to be
        instance functions against obj.
        Used by @spl.primitive_operator SPL cpp template.
    """
    ofns = list()
    for fn in obj._splpy_input_ports:
        ofns.append(getattr(obj, fn.__name__))
    return ofns
    

def _splpy_primitive_output_attrs(callable_, attributes):
    """Sets output attributes in the callable."""
    callable_._splpy_output_names = attributes


