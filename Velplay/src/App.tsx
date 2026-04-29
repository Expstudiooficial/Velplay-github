/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// @ts-nocheck
import { useState } from 'react';
import { PlayerProvider } from './contexts/PlayerContext';
import { AuthProvider } from './contexts/AuthContext';
import { Sidebar } from './components/Sidebar';
import { MainView } from './components/MainView';
import { Player } from './components/Player';
import { AuthModal } from './components/AuthModal';
import { SupabaseSetupWarning } from './components/SupabaseSetupWarning';
import { ErrorBoundary } from './components/ErrorBoundary';
import { Toaster } from 'sonner';
import { Home, Search, Library } from 'lucide-react';

export default function App() {
  const [currentView, setCurrentView] = useState('home');

  return (
    <ErrorBoundary>
      <AuthProvider>
        <PlayerProvider>
          <Toaster theme="dark" position="bottom-right" />
          <AuthModal />
          <SupabaseSetupWarning />
          <div className="h-screen w-screen bg-[#0a0a0f] text-white flex flex-col font-sans overflow-hidden selection:bg-indigo-500 selection:text-white">
            {/* Main Content Area */}
            <div className="flex flex-1 overflow-hidden pb-[136px] md:pb-0">
              <Sidebar currentView={currentView} setView={setCurrentView} />
              <ErrorBoundary>
                <MainView currentView={currentView} setView={setCurrentView} />
              </ErrorBoundary>
            </div>
            
            {/* Persistent Bottom Player */}
            <div className="z-20">
              <Player />
            </div>

            {/* Mobile Bottom Navigation */}
            <div className="md:hidden fixed bottom-0 left-0 right-0 h-16 bg-[#0a0a0f]/95 backdrop-blur-lg border-t border-white/10 flex justify-around items-center px-4 z-30">
              <button 
                onClick={() => setCurrentView('home')} 
                className={`flex flex-col items-center gap-1 ${currentView === 'home' ? 'text-white' : 'text-gray-400'}`}
              >
                <Home size={24} />
                <span className="text-[10px]">Home</span>
              </button>
              <button 
                onClick={() => setCurrentView('search')} 
                className={`flex flex-col items-center gap-1 ${currentView === 'search' ? 'text-white' : 'text-gray-400'}`}
              >
                <Search size={24} />
                <span className="text-[10px]">Search</span>
              </button>
              <button 
                onClick={() => setCurrentView('library')} 
                className={`flex flex-col items-center gap-1 ${currentView === 'library' || currentView.startsWith('playlist-') || currentView === 'liked-songs' ? 'text-white' : 'text-gray-400'}`}
              >
                <Library size={24} />
                <span className="text-[10px]">Library</span>
              </button>
            </div>
          </div>
        </PlayerProvider>
      </AuthProvider>
    </ErrorBoundary>
  );
}
