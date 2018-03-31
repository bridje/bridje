(ns bridje.quoter
  (:require [clojure.string :as str]))

;; jfc this needs some tests...
(defn expand-quotes [form {:keys [env] :as ctx}]
  (letfn [(quote-form [[form-type & [first-form :as forms]]]
            (case form-type
              :quote (quote-form (quote-form first-form))
              :splicing (let [[form-type & forms] first-form]
                          [:list
                           [:symbol (->form-adt-sym form-type)]
                           [:list
                            [:symbol 'concat]
                            (into [:vector] (map (comp quote-form #(vector :spliced %))) forms)]])

              :spliced (let [[form-type first-form :as form] first-form]
                         (if (= form-type :unquote-splicing)
                           (quote-form form)
                           [:vector (quote-form form)]))

              (:unquote :unquote-splicing) (expand-quotes* first-form)

              (into [:list [:symbol (->form-adt-sym form-type)]]
                    (case form-type
                      (:vector :list :set :record) [(into [:vector] (map quote-form forms))]
                      :symbol [[:list [:symbol 'symbol] [:symbol first-form]]]
                      [[form-type first-form]]))))

          (expand-syntax-quote [[form-type & [first-form :as forms] :as form] {:keys [splice?]}]
            (case form-type
              :unquote [:unquote first-form]

              :unquote-splicing (if splice?
                                  [:unquote-splicing first-form]
                                  (throw (ex-info "unquote-splicing used outside of collection" {:form form})))

              (:vector :set :list :record)
              (let [splice? (some (comp #{:unquote-splicing} first) forms)
                    inner-form (into [form-type] (map #(expand-syntax-quote % {:splice? splice?})) forms)]
                (if splice?
                  [:splicing inner-form]
                  inner-form))

              :syntax-quote [:quote (expand-syntax-quote first-form {:splice? false})]

              :quote [:quote (expand-syntax-quote first-form {:splice? false})]

              form))

          (expand-quotes* [[form-type & [first-form :as forms] :as form]]
            (case form-type
              (:vector :set :list :record) (into [form-type] (map expand-quotes*) forms)
              :quote (quote-form first-form)
              :syntax-quote (quote-form (expand-syntax-quote first-form {:splice? false}))
              :unquote (throw (ex-info "'unquote' outside of 'syntax-quote'" {:form form}))
              :unquote-splicing (throw (ex-info "'unquote-splicing' outside of 'syntax-quote'" {:form form}))

              form))]

    (expand-quotes* form)))

(comment
  (-> (first (bridje.reader/read-forms "`[1 ~@['2 '3 '4]]"))
      (expand-quotes {}))

  (-> (first (bridje.reader/read-forms "'(foo 4 [2 3])"))
      (expand-quotes {})))
