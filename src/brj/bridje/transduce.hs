{-# LANGUAGE GADTs, RankNTypes #-}

module Transduce where
import Prelude hiding (seq)

class Seqable s where
  seq :: s a -> Seq a

data Seq a where
  Cons :: Seqable s => a -> s a -> Seq a
  Nil :: Seq a

instance Seqable Seq where
  seq = id

instance Seqable [] where
  seq [] = Nil
  seq (x : xs) = Cons x $ seq xs

data Transducer x a where
  Transducer :: (x -> (forall s. Seqable s => s a -> r) -> r)
                -> IO Bool
                -> ((forall s. Seqable s => s a -> r) -> r)
                -> Transducer x a

data LazySeq a = LazySeq (IO a)

lazySeq = LazySeq . return

stepTransducer :: (x -> (forall s. Seqable s => s a -> r) -> r) -> Transducer x a
stepTransducer step = Transducer step (return True) (\c -> c Nil)

xfMap f = stepTransducer (\x c -> c [(f x)])
xfFilter f = stepTransducer (\x c -> if (f x) then c (Cons x Nil) else c Nil)
xfMapcat f = stepTransducer (\x c -> c (f x))

stream :: Seqable s => s a -> Transducer a b -> LazySeq b
stream coll (Transducer tstep tmore tcomplete) = case (seq coll) of
  Cons head tail -> undefined
  Nil -> undefined
