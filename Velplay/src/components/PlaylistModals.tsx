import React, { useState } from 'react';
import { supabase } from '../lib/supabase';
import { Track } from '../services/AudiusService';
import { toast } from 'sonner';
import { usePlaylists } from '../lib/db';
import { X } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { useAuth } from '../contexts/AuthContext';

export function CreatePlaylistModal({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) {
  const [name, setName] = useState('');

  const { user } = useAuth();
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !user) return;
    try {
      const { error } = await supabase.from('playlists').insert({
        user_id: user.id,
        name: name.trim(),
        createdAt: Date.now()
      });
      if (error) throw error;
      toast.success('Playlist created');
      setName('');
      onClose();
    } catch(err: any) {
      console.error(err);
      if (err.code === '42P01' || err.code === 'PGRST205') {
        window.dispatchEvent(new Event('supabase_missing_tables'));
        toast.error('Database setup required. Please check the setup instructions.');
      } else {
        toast.error(err.message || 'Failed to create playlist');
      }
    }
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 text-white">
        <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }} className="bg-[#161622] border border-[#1f1f2e] p-6 rounded-2xl w-full max-w-sm shadow-2xl relative">
          <button onClick={onClose} className="absolute top-4 right-4 text-gray-400 hover:text-white"><X size={20} /></button>
          <h2 className="text-xl font-bold mb-4">Create Playlist</h2>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <input 
              autoFocus
              type="text" 
              value={name}
              onChange={e => setName(e.target.value)}
              placeholder="Playlist name"
              className="w-full bg-[#0a0a0f] border border-[#1f1f2e] rounded-lg px-4 py-2.5 outline-none focus:border-indigo-500"
            />
            <div className="flex justify-end gap-2 mt-2">
              <button type="button" onClick={onClose} className="px-4 py-2 font-semibold text-gray-400 hover:text-white">Cancel</button>
              <button type="submit" disabled={!name.trim()} className="px-4 py-2 font-semibold bg-indigo-500 hover:bg-indigo-600 disabled:opacity-50 !text-white rounded-lg">Create</button>
            </div>
          </form>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}

export function AddToPlaylistModal({ isOpen, onClose, track }: { isOpen: boolean; onClose: () => void; track: Track | null }) {
  const { user } = useAuth();
  const playlists = usePlaylists(user);

  const addToPlaylist = async (playlistId: string) => {
    if (!track || !user) return;
    try {
      const { data: existing } = await supabase.from('playlist_tracks')
        .select('*')
        .eq('playlistId', playlistId)
        .eq('trackId', track.id)
        .single();
        
      if (existing) {
        toast.error('Track already in playlist');
        return;
      }
      const { error } = await supabase.from('playlist_tracks').insert({
        playlistId,
        trackId: track.id,
        trackData: track,
        addedAt: Date.now()
      });
      if (error) throw error;
      toast.success('Added to playlist');
      onClose();
    } catch(err: any) {
      console.error(err);
      if (err.code === '42P01' || err.code === 'PGRST205') {
        window.dispatchEvent(new Event('supabase_missing_tables'));
        toast.error('Database setup required. Please check the setup instructions.');
      } else {
        toast.error(err.message || 'Failed to add track');
      }
    }
  };

  if (!isOpen || !track) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 backdrop-blur-sm p-4 text-white">
        <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }} className="bg-[#161622] border border-[#1f1f2e] p-6 rounded-2xl w-full max-w-sm shadow-2xl relative">
          <button onClick={onClose} className="absolute top-4 right-4 text-gray-400 hover:text-white"><X size={20} /></button>
          <h2 className="text-xl font-bold mb-4">Add to Playlist</h2>
          <div className="flex flex-col gap-2 max-h-60 overflow-y-auto custom-scrollbar pr-2">
             {playlists?.length === 0 && <p className="text-gray-400 text-sm">No playlists found. Create one first.</p>}
             {playlists?.map(p => (
               <button 
                 key={p.id}
                 onClick={() => addToPlaylist(p.id!)}
                 className="w-full text-left px-4 py-3 bg-[#0a0a0f] hover:bg-indigo-500/20 rounded-lg transition-colors font-medium truncate"
               >
                 {p.name}
               </button>
             ))}
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
}
