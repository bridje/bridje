{-# LANGUAGE GADTs, RankNTypes, ScopedTypeVariables #-}

module Transduce where
import System.IO.Unsafe (unsafePerformIO)
import Prelude hiding (seq)
import Data.IORef
import Data.List (intercalate)

ifM pred t e = do p' <- pred; if p' then t else e

class Seqable s where
  seq :: s a -> Seq a

data Seq a where
  Cons :: Seqable s => a -> s a -> Seq a
  Nil :: Seq a

showSeq :: (Seqable s, Show a) => s a -> String
showSeq s = "Seq [" ++ (intercalate ", " . showSeq') s ++ "]"
  where showSeq' :: (Seqable s, Show a) => s a -> [String]
        showSeq' s = case (seq s) of
                       Cons head tail -> show head : showSeq' tail
                       Nil -> []

instance (Show a) => Show (LazySeq a) where
  show = showSeq

instance (Show a) => Show (Seq a) where
  show = showSeq

instance Seqable Seq where
  seq = id

instance Seqable [] where
  seq [] = Nil
  seq (x : xs) = Cons x $ seq xs

instance Seqable LazySeq where
  seq (LazySeq f) = unsafePerformIO f

data Transducer x a where
  Transducer :: (forall r. (x -> (forall s. Seqable s => s a -> r) -> IO r))
                -> IO Bool
                -> (forall r. (forall s. Seqable s => s a -> r) -> IO r)
                -> Transducer x a

data LazySeq a = LazySeq (IO (Seq a))

lazySeq = LazySeq . return

stepTransducer :: (forall r. x -> (forall s. Seqable s => s a -> r) -> IO r) -> Transducer x a
stepTransducer step = Transducer step (return True) (\c -> return $ c Nil)

xfMap f = stepTransducer (\x c -> return $ c [(f x)])
xfFilter f = stepTransducer (\x c -> return $ if (f x) then c (Cons x Nil) else c Nil)
xfMapcat f = stepTransducer (\x c -> return $ c (f x))

lazyCat :: Seq (Seq a) -> LazySeq a
lazyCat seqs = LazySeq $ return $ case seqs of
                                    Cons headSeq tailSeqs -> case (seq headSeq) of
                                                               Cons head tail -> Cons head (lazyCat (Cons (seq tail) tailSeqs))
                                                               Nil -> seq $ lazyCat (seq tailSeqs)
                                    Nil -> Nil

stream :: Seqable s => s a -> Transducer a b -> LazySeq b
stream coll t@(Transducer tstep tmore tcomplete) =
  LazySeq $ case (seq coll) of
              Cons head tail -> do els <- tstep head seq
                                   more' <- tmore
                                   tail <- if more'
                                           then (let (LazySeq io) = stream tail t in io)
                                           else tcomplete seq
                                   return $ seq $ lazyCat $ Cons els (Cons (seq tail) Nil)

              Nil -> return Nil

data Reducer x a where
  Reducer :: (x -> IO ()) -> (IO Bool) -> (IO a) -> Reducer x a

toVec = do
  vec <- newIORef []
  return $ Reducer
    (\x -> do modifyIORef vec (\v -> v ++ [x]))
    (return True)
    (readIORef vec)

comp2t :: Transducer x b -> Transducer a x -> Transducer a b
comp2t l@(Transducer lstep lmore lcomplete) r@(Transducer rstep rmore rcomplete) =
  undefined

reducer :: forall x a b. Transducer x a -> Reducer a b -> Reducer x b
reducer t@(Transducer tstep tmore tcomplete) r@(Reducer rstep rmore rcomplete) =
  let step :: Seqable s => s a -> IO ()
      step els = case (seq els) of
                   Cons el tail -> do rstep el
                                      rmore' <- rmore
                                      if rmore' then step tail else return ()
                   Nil -> return ()

  in Reducer (\x -> tstep x seq >>= step)

             (do t' <- tmore; r' <- rmore; return $ t' && r')

             (do rmore' <- rmore
                 if rmore' then tcomplete seq >>= step else return ()
                 rcomplete)

reduce :: forall s x a. Seqable s => s x -> Reducer x a -> IO a
reduce coll r@(Reducer rstep rmore rcomplete) =
  case (seq coll) of
    Cons el tail -> do rstep el
                       rmore' <- rmore
                       if rmore' then reduce tail r else rcomplete
    Nil -> rcomplete
