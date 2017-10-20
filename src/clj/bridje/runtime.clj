(ns bridje.runtime)

(defrecord ADT [adt-type params])

(deftype Symbol [ns sym])

(def ->Symbol
  (-> (fn
        ([sym] (Symbol. nil sym))
        ([ns sym] (Symbol. ns sym)))

      memoize))
