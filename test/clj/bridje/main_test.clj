(ns bridje.main-test
  (:require [bridje.main :as sut]
            [bridje.runtime :as rt]
            [clojure.test :as t]
            [bridje.fake-io :refer [fake-file fake-io]]))

(t/deftest e2e-test
  (let [fake-files {'bridje.foo (fake-file
                                 '(ns bridje.foo)

                                 '(def (flip x y)
                                    [y x])

                                 '(defdata Nothing)
                                 '(defdata (Just a)))

                    'bridje.bar (fake-file
                                 '(ns bridje.bar
                                    {aliases {foo bridje.foo}})

                                 '(def (main args)
                                    (let [seq ["ohno"]
                                          just (foo/->Just "just")
                                          nothing foo/Nothing]
                                      {message (foo/flip "World" "Hello")
                                       seq seq
                                       the-nothing nothing
                                       just just
                                       justtype (match just
                                                       foo/Just "it's a just"
                                                       foo/Nothing "it's nothing"
                                                       "it's something else")
                                       justval (foo/Just->a just)})))}

        {:keys [compiler-io !compiled-files]} (fake-io {:source-files fake-files})]

    (bridje.compiler/compile! 'bridje.bar {:io compiler-io, :env {}})

    (t/is (= (sut/run-main {:main-ns 'bridje.bar}
                           {:io compiler-io})

             {:message ["Hello" "World"],
              :seq ["ohno"],
              :the-nothing (rt/->ADT 'bridje.foo/Nothing {}),
              :just (rt/->ADT 'bridje.foo/Just {:a "just"}),
              :justtype "it's a just"
              :justval "just"}))))
