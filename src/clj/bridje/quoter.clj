(ns bridje.quoter
  (:require [clojure.string :as str]))

(def ->form-adt-sym
  (-> (fn [form-type]
        (let [base (->> (str/split (name form-type) #"-")
                        (map str/capitalize)
                        str/join)]
          (symbol (str base "Form"))))
      memoize))

(defn expand-quotes [form {:keys [env] :as ctx}]
  (letfn [(quote-form [[form-type & [first-form :as forms] :as form]]
            (case form-type
              :quote (quote-form (expand-quotes* first-form))

              (into [:list [:symbol (->form-adt-sym form-type)]]
                    (case form-type
                      (:vector :list :set :record) [(into [:vector] (map quote-form forms))]
                      :symbol [[:list [:symbol 'quote] [:symbol first-form]]]
                      [[form-type first-form]]))))

          (expand-syntax-quote [[form-type & [first-form :as forms] :as form] {:keys [splice?]}]
            (let [expanded-form (case form-type
                                  :unquote (expand-quotes* first-form)

                                  :unquote-splicing (if splice?
                                                      (expand-quotes* first-form)
                                                      (throw (ex-info "unquote-splicing used outside of collection" {:form form})))

                                  (:vector :set :list :record)
                                  (let [splice? (some (comp #{:unquote-splicing} first) forms)
                                        inner-forms (into [:vector] (map #(expand-syntax-quote % {:splice? splice?})) forms)]
                                    [:list
                                     [:symbol (->form-adt-sym form-type)]
                                     (if splice?
                                       [:list [:symbol 'concat] inner-forms]
                                       inner-forms)])

                                  :syntax-quote (-> first-form
                                                    (expand-syntax-quote {:splice? false})
                                                    (expand-syntax-quote {:splice? false}))

                                  :quote (-> first-form
                                             (expand-syntax-quote {:splice? false})
                                             quote-form)

                                  (quote-form form))]
              (if (and splice? (not= :unquote-splicing form-type))
                [:vector expanded-form]
                expanded-form)))

          (expand-quotes* [[form-type & [first-form :as forms] :as form]]
            (case form-type
              (:vector :set :list :record) (into [form-type] (map expand-quotes*) forms)
              :quote (quote-form (expand-quotes* first-form))
              :syntax-quote (expand-syntax-quote first-form {:splice? false})

              form))]

    (expand-quotes* form)))
