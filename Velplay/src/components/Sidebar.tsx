import { Home, Search, Library, PlusSquare, Heart, Download, User, LogOut, Music } from 'lucide-react';
import { exportDB, usePlaylists } from '../lib/db';
import { useAuth } from '../contexts/AuthContext';
import { useState } from 'react';
import { CreatePlaylistModal } from './PlaylistModals';

export function Sidebar({ currentView, setView }: { currentView: string; setView: (v: string) => void }) {
  const { user, setShowAuthModal, logout, requireAuth } = useAuth();
  const playlists = usePlaylists(user);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  
  return (
    <div className="hidden md:flex w-64 h-full bg-[#0a0a0f]/80 backdrop-blur-xl flex-col p-6 font-sans shrink-0 border-r border-[#1f1f2e]">
      <div className="text-white font-bold text-2xl mb-8 flex items-center gap-3">
        <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center shadow-[0_0_15px_rgba(99,102,241,0.5)]">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="text-white">
            <polygon points="5 3 19 12 5 21 5 3" fill="currentColor"/>
          </svg>
        </div>
        <span className="tracking-tight text-transparent bg-clip-text bg-gradient-to-r from-white to-gray-400">Velplay</span>
      </div>

      <nav className="flex flex-col gap-4 text-sm font-semibold text-gray-400">
        <button 
          onClick={() => setView('home')} 
          className={`flex items-center gap-4 hover:text-white transition-colors ${currentView === 'home' ? 'text-white' : ''}`}
        >
          <Home size={20} />
          Home
        </button>
        <button 
          onClick={() => setView('search')} 
          className={`flex items-center gap-4 hover:text-white transition-colors ${currentView === 'search' ? 'text-white' : ''}`}
        >
          <Search size={20} />
          Search
        </button>
        <button 
          onClick={() => requireAuth(() => setView('library'))} 
          className={`flex items-center gap-4 hover:text-white transition-colors ${currentView === 'library' ? 'text-white' : ''}`}
        >
          <Library size={20} />
          Your Library
        </button>
      </nav>

      <div className="mt-8 pt-6 border-t border-[#1f1f2e] flex flex-col gap-4 text-sm font-semibold text-gray-400 flex-1 min-h-0">
        {user ? (
          <div className="flex items-center justify-between px-2 mb-2 bg-[#1f1f2e]/50 rounded-lg py-2 shrink-0">
            <div className="flex items-center gap-3 text-white">
              <div className="w-7 h-7 rounded-full bg-indigo-500 flex items-center justify-center text-xs font-bold shadow-sm">{user.email ? user.email.charAt(0).toUpperCase() : '?'}</div>
              <span className="truncate max-w-[100px]">{user.email ? user.email.split('@')[0] : 'User'}</span>
            </div>
            <button onClick={logout} className="p-1 text-gray-400 hover:text-white transition-colors" title="Sign out"><LogOut size={16} /></button>
          </div>
        ) : (
          <button onClick={() => setShowAuthModal(true)} className="flex items-center gap-4 hover:text-white transition-colors group mb-2 shrink-0">
            <div className="w-7 h-7 bg-gray-600/50 group-hover:bg-indigo-500 flex items-center justify-center rounded-full text-gray-300 group-hover:text-white transition-colors">
              <User size={14} />
            </div>
            Sign In / Sign Up
          </button>
        )}

        <button 
          onClick={() => requireAuth(() => setIsCreateOpen(true))}
          className="flex items-center gap-4 hover:text-white transition-colors group shrink-0"
        >
          <div className="w-6 h-6 bg-gray-600/50 group-hover:bg-white flex items-center justify-center rounded-sm text-gray-300 group-hover:text-black transition-colors">
            <PlusSquare size={14} />
          </div>
          Create Playlist
        </button>
        <button 
          onClick={() => requireAuth(() => setView('liked-songs'))}
          className={`flex items-center gap-4 hover:text-white transition-colors group shrink-0 ${currentView === 'liked-songs' ? 'text-white' : ''}`}
        >
          <div className="w-6 h-6 bg-gradient-to-br from-indigo-500 to-purple-500 flex items-center justify-center rounded-sm text-white shadow-lg">
            <Heart size={14} fill="currentColor" />
          </div>
          Liked Songs
        </button>

        <div className="mt-4 flex-1 min-h-0 overflow-y-auto custom-scrollbar flex flex-col gap-3 pb-2 pt-2 border-t border-[#1f1f2e]">
          {playlists?.map((p) => (
            <button 
              key={p.id}
              onClick={() => requireAuth(() => setView(`playlist-${p.id}`))}
              className={`text-left text-sm hover:text-white truncate flex items-center gap-2 ${currentView === `playlist-${p.id}` ? 'text-indigo-400 font-bold' : 'text-gray-400'}`}
            >
              <Music size={14} className="shrink-0 opacity-50" />
              {p.name}
            </button>
          ))}
        </div>
      </div>
      
      <div className="mt-auto flex flex-col gap-4 pt-4 border-t border-[#1f1f2e] shrink-0">
        <button 
          onClick={exportDB}
          className="flex items-center gap-4 text-xs font-semibold text-gray-400 hover:text-white transition-colors"
        >
          <Download size={14} />
          Export Library Backup
        </button>
        <div className="flex flex-col gap-1 text-[10px] text-gray-500 mt-2">
          <span>Made by Velora tm owned by Exp_studio</span>
          <span>Made witch Love to free apps.</span>
        </div>
      </div>
      
      <CreatePlaylistModal isOpen={isCreateOpen} onClose={() => setIsCreateOpen(false)} />
    </div>
  );
}
