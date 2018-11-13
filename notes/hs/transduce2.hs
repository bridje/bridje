{-# LANGUAGE GADTs, RankNTypes, ScopedTypeVariables #-}

module Transduce2 where
import Prelude hiding (map, filter, reduce)

data Reducer x s a where
  Reducer :: s -> (s -> x -> s) -> (s -> a) -> Reducer x s a

stepTransducer :: (s -> (s -> b -> s) -> a -> s) -> Reducer b s o -> Reducer a s o
stepTransducer step (Reducer init innerStep complete) =
  Reducer init newStep complete
    where newStep s el = step s innerStep el

map :: (a -> b) -> Reducer b s o -> Reducer a s o
map f = stepTransducer step
  where step s innerStep a = innerStep s (f a)

filter f = stepTransducer step
  where step s innerStep a = if (f a) then innerStep s a else s

reduce :: [a] -> Reducer a s o -> o
reduce [] (Reducer state _ complete) = complete state
reduce (x:xs) (Reducer state step complete) = reduce xs (Reducer (step state x) step complete)

toVec :: Reducer a [a] [a]
toVec = Reducer [] (\coll el -> coll ++ [el]) id

foo = reduce [1..4] (map (* 2) $ filter (> 2) $ map (+ 1) toVec)
