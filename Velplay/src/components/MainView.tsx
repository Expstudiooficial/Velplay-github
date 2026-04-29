import React, { useEffect, useState } from 'react';
import { audiusService, type Track } from '../services/AudiusService';
import { useMusicPlayer } from '../hooks/useMusicPlayer';
import { useAuth } from '../contexts/AuthContext';
import { VirtualList } from './VirtualList';
import { Play, Heart, Plus, Trash2, Music } from 'lucide-react';
import { motion } from 'motion/react';
import { supabase } from '../lib/supabase';
import { useLikedTracks, usePlaylistTracks, usePlaylists } from '../lib/db';
import { toast } from 'sonner';
import { AddToPlaylistModal } from './PlaylistModals';

export function MainView({ currentView, setView }: { currentView: string; setView: (v: string) => void }) {
  const [trending, setTrending] = useState<Track[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<Track[]>([]);
  const [trackToAdd, setTrackToAdd] = useState<Track | null>(null);
  
  const { playTrack, currentTrack, isPlaying } = useMusicPlayer();
  const { user, requireAuth } = useAuth();

  const likedTracks = useLikedTracks(user);

  const isPlaylistView = currentView.startsWith('playlist-');
  const playlistIdStr = isPlaylistView ? currentView.split('-').slice(1).join('-') : null;
  const playlistId = playlistIdStr || null;
  const playlistTracks = usePlaylistTracks(user, playlistId);
  const playlists = usePlaylists(user);
  const currentPlaylist = playlists.find(p => p.id === playlistId);

  useEffect(() => {
    if (currentView === 'home' && trending.length === 0) {
      loadTrending();
    }
  }, [currentView]);

  // Debounced search
  useEffect(() => {
    if (currentView === 'search' && searchQuery.trim().length > 2) {
      const timer = setTimeout(() => {
        handleSearch();
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [searchQuery, currentView]);

  const loadTrending = async () => {
    setLoading(true);
    try {
      const tracks = await audiusService.getTrendingTracks(20, 0);
      setTrending(tracks);
    } catch (e) {
      console.error(e);
      toast.error('Failed to load trending tracks');
    } finally {
      setLoading(false);
    }
  };

  const loadMoreTrending = async () => {
    if (loadingMore) return;
    setLoadingMore(true);
    try {
      const moreTracks = await audiusService.getTrendingTracks(20, trending.length);
      setTrending(prev => [...prev, ...moreTracks]);
    } catch (e) {
      toast.error('Failed to load more');
    } finally {
      setLoadingMore(false);
    }
  };

  const handleSearch = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!searchQuery.trim()) return;
    setLoading(true);
    try {
      const results = await audiusService.searchTracks(searchQuery, 20, 0);
      setSearchResults(results);
    } catch (e) {
      console.error(e);
      toast.error('Search failed');
    } finally {
      setLoading(false);
    }
  };

  const loadMoreSearch = async () => {
    if (loadingMore || !searchQuery.trim()) return;
    setLoadingMore(true);
    try {
      const moreTracks = await audiusService.searchTracks(searchQuery, 20, searchResults.length);
      setSearchResults(prev => [...prev, ...moreTracks]);
    } catch (e) {
      toast.error('Failed to load more');
    } finally {
      setLoadingMore(false);
    }
  };

  const toggleLike = async (e: React.MouseEvent, track: Track) => {
    e.stopPropagation();
    requireAuth(async () => {
      if (!user) return;
      try {
        const existing = likedTracks?.find(lt => lt.trackId === track.id);
        if (existing) {
          const { error } = await supabase.from('liked_tracks').delete().eq('id', existing.id);
          if (error) throw error;
          toast.success('Removed from Liked Songs');
        } else {
          const { error } = await supabase.from('liked_tracks').insert({
            user_id: user.id,
            trackId: track.id,
            trackData: track,
            likedAt: Date.now()
          });
          if (error) throw error;
          toast.success('Added to Liked Songs');
        }
      } catch (err: any) {
        console.error(err);
        if (err.code === '42P01' || err.code === 'PGRST205') {
          window.dispatchEvent(new Event('supabase_missing_tables'));
          toast.error('Database setup required. Please check the setup instructions.');
        } else {
          toast.error(err.message || 'Failed to update liked songs');
        }
      }
    });
  };

  const renderTrack = (track: Track, index: number, isSearchView = false, isLibraryView = false, isPlaylist = false, pTrackId?: string) => {
    const queue = isLibraryView ? (likedTracks?.map(lt => lt.trackData) || []) : 
                  (isPlaylist && playlistTracks ? playlistTracks.map(pt => pt.trackData) : 
                  (isSearchView ? searchResults : trending));
    
    const isCurrentTrack = currentTrack?.id === track.id;
    const isLiked = likedTracks?.some(lt => lt.trackId === track.id);

    return (
      <div 
        key={pTrackId || track.id}
        className={`flex items-center gap-4 p-2 rounded-md hover:bg-white/10 group cursor-pointer transition-colors ${isCurrentTrack ? 'bg-white/5' : ''}`}
        onClick={() => playTrack(track, queue)}
        onContextMenu={(e) => {
          e.preventDefault();
          requireAuth(() => setTrackToAdd(track));
        }}
      >
        <div className="w-8 text-center text-gray-400 font-mono text-sm relative">
          {isCurrentTrack && isPlaying ? (
            <div className="flex items-end justify-center gap-1 h-4">
              <div className="w-1 bg-indigo-500 h-full animate-pulse"></div>
              <div className="w-1 bg-indigo-500 h-2/3 animate-pulse delay-75"></div>
              <div className="w-1 bg-indigo-500 h-3/4 animate-pulse delay-150"></div>
            </div>
          ) : (
            <>
              <span className="group-hover:hidden">{index + 1}</span>
              <Play size={14} className="hidden group-hover:inline-block fill-white" />
            </>
          )}
        </div>
        
        <img 
          src={track.artwork?.['150x150'] || track.artwork?.['480x480'] || 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=150'} 
          onError={(e) => { e.currentTarget.src = 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=150'; }}
          className="w-10 h-10 rounded shadow-sm object-cover"
          loading="lazy"
        />
        
        <div className="flex flex-col flex-1 overflow-hidden">
          <span className={`truncate font-semibold ${isCurrentTrack ? 'text-indigo-400' : 'text-white'}`}>{track.title}</span>
          <span className="text-sm text-gray-400 truncate">{track.user?.name || 'Unknown Artist'}</span>
        </div>

        <button 
          onClick={(e) => { e.stopPropagation(); requireAuth(() => setTrackToAdd(track)); }} 
          className="text-gray-400 hover:text-white px-2 transition-colors opacity-100 md:opacity-0 md:group-hover:opacity-100"
          title="Add to Playlist"
        >
          <Plus size={16} />
        </button>

        <button onClick={(e) => toggleLike(e, track)} className="text-gray-400 hover:text-white px-2 transition-colors">
          <Heart size={16} className={isLiked ? 'fill-indigo-500 text-indigo-500' : ''} />
        </button>

        {isPlaylist && pTrackId && (
          <button 
            onClick={async (e) => {
               e.stopPropagation();
               try {
                 const { error } = await supabase.from('playlist_tracks').delete().eq('id', pTrackId);
                 if (error) throw error;
                 toast.success('Removed from playlist');
               } catch (err: any) {
                 console.error(err);
                 if (err.code === '42P01' || err.code === 'PGRST205') {
                   window.dispatchEvent(new Event('supabase_missing_tables'));
                   toast.error('Database setup required. Please check the setup instructions.');
                 } else {
                   toast.error(err.message || 'Failed to remove from playlist');
                 }
               }
            }} 
            className="text-gray-400 hover:text-red-500 px-2 transition-colors opacity-100 md:opacity-0 md:group-hover:opacity-100"
            title="Remove from Playlist"
          >
            <Trash2 size={16} />
          </button>
        )}

        <div className="w-16 text-right text-sm text-gray-400 pr-2">
          {Math.floor(track.duration / 60)}:{(track.duration % 60).toString().padStart(2, '0')}
        </div>
      </div>
    );
  };

  return (
    <div className="flex-1 bg-gradient-to-b from-[#161622] to-[#0a0a0f] overflow-hidden flex flex-col relative text-white">
      {/* Top Nav Overlay */}
      <div className="h-16 px-6 flex items-center shrink-0 z-10 sticky top-0 bg-[#0a0a0f]/40 backdrop-blur-md">
        {currentView === 'search' && (
          <form onSubmit={handleSearch} className="flex-1 max-w-sm">
            <input 
              type="text" 
              placeholder="Search for tracks, artists..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="w-full bg-white/5 text-white rounded-full px-5 py-2.5 text-sm font-semibold outline-none border border-transparent focus:border-indigo-500/50 focus:bg-white/10 transition-colors"
            />
          </form>
        )}
      </div>

      <div className="flex-1 overflow-hidden px-6 pb-24 flex flex-col">
        {currentView === 'home' && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex-1 flex flex-col min-h-0">
            <h1 className="text-4xl font-bold mb-6 mt-4 tracking-tight shrink-0">Discover</h1>
            {loading ? (
              <div className="flex flex-col gap-2 animate-pulse mt-4">
                {[1,2,3,4,5,6,7].map(i => (
                  <div key={i} className="flex items-center gap-4 p-2">
                    <div className="w-8"></div>
                    <div className="w-10 h-10 bg-white/10 rounded"></div>
                    <div className="flex-1 flex flex-col gap-2">
                      <div className="w-1/3 h-4 bg-white/10 rounded"></div>
                      <div className="w-1/4 h-3 bg-white/10 rounded"></div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex-1 -mx-2 px-2 min-h-0 relative">
                <VirtualList
                  items={trending}
                  itemHeight={64}
                  renderItem={(track: any, i: number) => renderTrack(track, i, false)}
                  containerHeight="100%"
                  footerHeight={80}
                  Footer={
                    <div className="p-4 flex justify-center">
                      <button 
                        onClick={loadMoreTrending}
                        disabled={loadingMore}
                        className="px-6 py-2 bg-indigo-500 hover:bg-indigo-600 disabled:opacity-50 rounded-full font-semibold text-sm transition-colors"
                      >
                        {loadingMore ? 'Loading...' : 'Show More'}
                      </button>
                    </div>
                  }
                />
              </div>
            )}
          </motion.div>
        )}

        {currentView === 'search' && (
           <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex-1 flex flex-col min-h-0">
             <h1 className="text-2xl font-bold mb-6 mt-4 tracking-tight shrink-0">Search Results</h1>
             {loading && searchQuery ? (
               <div className="flex space-x-2 text-indigo-500"><span className="animate-bounce">...</span></div>
             ) : (
               <div className="flex-1 -mx-2 px-2 min-h-0 relative">
                 {searchResults.length > 0 ? (
                   <VirtualList
                     items={searchResults}
                     itemHeight={64}
                     renderItem={(track: any, i: number) => renderTrack(track, i, true)}
                     containerHeight="100%"
                     footerHeight={80}
                     Footer={
                       <div className="p-4 flex justify-center">
                         <button 
                           onClick={loadMoreSearch}
                           disabled={loadingMore}
                           className="px-6 py-2 bg-indigo-500 hover:bg-indigo-600 disabled:opacity-50 rounded-full font-semibold text-sm transition-colors"
                         >
                           {loadingMore ? 'Loading...' : 'Show More'}
                         </button>
                       </div>
                     }
                   />
                 ) : (
                   <div className="text-gray-400 mt-10">Search for tracks, artists, or podcasts.</div>
                 )}
               </div>
             )}
           </motion.div>
        )}

        {currentView === 'library' && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex-1 flex flex-col min-h-0">
            <h1 className="text-4xl font-bold mb-6 mt-4 tracking-tight shrink-0">Your Library</h1>
            
            <div className="flex-1 overflow-y-auto custom-scrollbar pr-2 pb-4">
              {/* Playlists Grid */}
              <div className="mb-8">
                <h2 className="text-xl font-bold mb-4">Playlists</h2>
                {(playlists && playlists.length > 0) ? (
                  <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                    {playlists.map(p => (
                      <div 
                        key={p.id}
                        onClick={() => {
                          setView(`playlist-${p.id}`);
                        }}
                        className="bg-white/5 hover:bg-white/10 transition-colors p-4 rounded-xl cursor-pointer group flex flex-col gap-3"
                      >
                        <div className="w-full aspect-square bg-gradient-to-br from-indigo-500/20 to-purple-500/20 rounded-lg flex items-center justify-center shadow-inner group-hover:shadow-lg transition-all relative overflow-hidden">
                           <Music size={40} className="text-indigo-400/50 group-hover:text-indigo-400 transition-colors" />
                           <div className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                             <Play size={24} className="fill-white text-white" />
                           </div>
                        </div>
                        <span className="font-semibold truncate">{p.name}</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="text-gray-400 text-sm">No playlists yet. Create one from the sidebar!</div>
                )}
              </div>

              {/* Liked Songs Container */}
              <div className="flex-1 min-h-[300px] flex flex-col border-t border-white/10 pt-6">
                <h2 className="text-xl font-bold mb-4 flex items-center gap-2">
                  <Heart size={20} className="fill-indigo-500 text-indigo-500" />
                  Liked Songs
                </h2>
                <div className="flex-1 relative">
                     {likedTracks && likedTracks.length > 0 ? (
                       <VirtualList
                         items={likedTracks.map(lt => lt.trackData)}
                         itemHeight={64}
                         renderItem={(track: any, i: number) => renderTrack(track, i, false, true)}
                         containerHeight="100%"
                       />
                     ) : (
                       <div className="text-gray-400 mt-4 text-sm">Songs you like will appear here.</div>
                     )}
                </div>
              </div>
            </div>
          </motion.div>
        )}

        {currentView === 'liked-songs' && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex-1 flex flex-col min-h-0">
            <h1 className="text-4xl font-bold mb-6 mt-4 tracking-tight shrink-0 flex items-center gap-4">
              <div className="w-16 h-16 bg-gradient-to-br from-indigo-500 to-purple-500 flex items-center justify-center rounded-lg shadow-lg">
                <Heart size={32} fill="currentColor" />
              </div>
              Liked Songs
            </h1>
            <div className="flex-1 -mx-2 px-2 min-h-0 relative">
                 {likedTracks && likedTracks.length > 0 ? (
                   <VirtualList
                     items={likedTracks.map(lt => lt.trackData)}
                     itemHeight={64}
                     renderItem={(track: any, i: number) => renderTrack(track, i, false, true)}
                     containerHeight="100%"
                   />
                 ) : (
                   <div className="text-gray-400 mt-10">Songs you like will appear here.</div>
                 )}
            </div>
          </motion.div>
        )}

        {isPlaylistView && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex-1 flex flex-col min-h-0">
            <h1 className="text-4xl font-bold mb-6 mt-4 tracking-tight shrink-0">{currentPlaylist?.name || "Playlist"}</h1>
            <div className="flex-1 -mx-2 px-2 min-h-0 relative">
                 {playlistTracks && playlistTracks.length > 0 ? (
                   <VirtualList
                     items={playlistTracks}
                     itemHeight={64}
                     renderItem={(item: any, i: number) => renderTrack(item.trackData, i, false, false, true, item.id)}
                     containerHeight="100%"
                   />
                 ) : (
                   <div className="text-gray-400 mt-10">This playlist is empty. Add some tracks!</div>
                 )}
            </div>
          </motion.div>
        )}
      </div>

      <AddToPlaylistModal 
        isOpen={!!trackToAdd} 
        onClose={() => setTrackToAdd(null)} 
        track={trackToAdd} 
      />
    </div>
  );
}
