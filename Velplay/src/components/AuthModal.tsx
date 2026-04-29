import React, { useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { motion, AnimatePresence } from 'motion/react';
import { X } from 'lucide-react';
import { supabase } from '../lib/supabase';
import { toast } from 'sonner';

export function AuthModal() {
  const { showAuthModal, setShowAuthModal } = useAuth();
  const [isLogin, setIsLogin] = useState(true);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  if (!showAuthModal) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !password) return;
    setLoading(true);
    setErrorMsg('');
    
    try {
      if (isLogin) {
        const { error } = await supabase.auth.signInWithPassword({
          email,
          password
        });
        if (error) throw error;
        toast.success("Successfully signed in");
        setShowAuthModal(false);
      } else {
        const { error } = await supabase.auth.signUp({
          email,
          password
        });
        if (error) throw error;
        toast.success("Successfully signed up! You may need to verify your email.");
      }
    } catch (err: any) {
      let errorMessage = err.message || 'Authentication failed';
      const lowerErr = errorMessage.toLowerCase();
      
      if (lowerErr.includes('user already registered')) {
        errorMessage = 'This email is already associated with an account. Please sign in instead.';
      } else if (lowerErr.includes('invalid login credentials') || lowerErr.includes('not found')) {
        errorMessage = 'Sorry, this account doesn\'t exist or the password is incorrect.';
      } else if (lowerErr.includes('password') && (lowerErr.includes('at least 6 characters') || lowerErr.includes('weak'))) {
        errorMessage = 'Your password must be at least 6 characters long.';
      }
      
      setErrorMsg(errorMessage);
      toast.error(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
        <motion.div 
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.95 }}
          className="bg-[#161622] border border-[#1f1f2e] p-6 rounded-2xl w-full max-w-md shadow-2xl relative"
        >
          <button 
            onClick={() => setShowAuthModal(false)}
            className="absolute top-4 right-4 text-gray-400 hover:text-white"
          >
            <X size={20} />
          </button>
          
          <h2 className="text-2xl font-bold text-white mb-6 text-center">
            {isLogin ? 'Sign in to Velplay' : 'Create an Account'}
          </h2>
          
          {errorMsg && (
            <div className="mb-4 bg-red-500/10 border border-red-500/50 text-red-500 px-4 py-3 rounded-lg text-sm font-medium">
              {errorMsg}
            </div>
          )}

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div>
              <label className="block text-sm font-semibold text-gray-300 mb-1">Email</label>
              <input 
                type="email" 
                required
                value={email}
                onChange={e => setEmail(e.target.value)}
                className="w-full bg-[#0a0a0f] border border-[#1f1f2e] rounded-lg px-4 py-2.5 text-white focus:outline-none focus:border-indigo-500 transition-colors"
                placeholder="Enter your email"
              />
            </div>
            
            <div>
              <label className="block text-sm font-semibold text-gray-300 mb-1">Password</label>
              <input 
                type="password" 
                required
                value={password}
                onChange={e => setPassword(e.target.value)}
                className="w-full bg-[#0a0a0f] border border-[#1f1f2e] rounded-lg px-4 py-2.5 text-white focus:outline-none focus:border-indigo-500 transition-colors"
                placeholder="Enter password"
              />
            </div>
            
            <button 
              type="submit"
              disabled={loading}
              className="mt-4 w-full bg-indigo-500 hover:bg-indigo-600 disabled:opacity-50 text-white font-bold py-3 rounded-full transition-colors"
            >
              {loading ? 'Processing...' : (isLogin ? 'Sign In' : 'Sign Up')}
            </button>
          </form>

          <div className="mt-6 text-center text-sm text-gray-400">
            {isLogin ? "Don't have an account? " : "Already have an account? "}
            <button 
              onClick={() => {
                setIsLogin(!isLogin);
                setErrorMsg('');
                setPassword('');
              }}
              type="button"
              className="text-indigo-400 hover:text-indigo-300 font-semibold"
            >
              {isLogin ? 'Sign Up' : 'Sign In'}
            </button>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
