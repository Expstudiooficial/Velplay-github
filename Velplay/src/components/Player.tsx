import { useMusicPlayer } from '../hooks/useMusicPlayer';
import { useAuth } from '../contexts/AuthContext';
import { Play, Pause, SkipBack, SkipForward, Volume2, MonitorSpeaker, Shuffle, Repeat } from 'lucide-react';
import { useMemo, useEffect, useState } from 'react';

export function Player() {
  const { currentTrack, isPlaying, duration, togglePlayPause, nextTrack, prevTrack, seek, volume, setVolume, isShuffling, isRepeating, toggleShuffle, toggleRepeat, audioRef } = useMusicPlayer();
  const { requireAuth } = useAuth();
  
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    const audio = audioRef?.current;
    if (!audio) return;

    let animationFrameId: number;
    const updateProgress = () => {
      if (duration > 0 && !audio.paused) {
        setProgress(audio.currentTime / duration);
      }
      animationFrameId = requestAnimationFrame(updateProgress);
    };

    if (isPlaying) {
      animationFrameId = requestAnimationFrame(updateProgress);
    } else {
      if (duration > 0) {
         setProgress(audio.currentTime / duration);
      }
    }

    return () => cancelAnimationFrame(animationFrameId);
  }, [isPlaying, duration, audioRef]);

  const handleSeek = (newProgress: number) => {
    setProgress(newProgress);
    seek(newProgress);
  };

  const formatTime = (timeInSeconds: number) => {
    if (isNaN(timeInSeconds) || timeInSeconds < 0) return '0:00';
    const m = Math.floor(timeInSeconds / 60);
    const s = Math.floor(timeInSeconds % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const currentSeconds = progress * duration;

  return (
    <div className="h-14 md:h-24 bg-[#0a0a0f] md:bg-[#181818] border-t border-[#1f1f2e] fixed bottom-16 md:bottom-0 left-0 right-0 z-50 flex items-center justify-between px-2 md:px-4 text-white rounded-lg md:rounded-none m-2 md:m-0">
      {/* Left: Track Info */}
      <div className="w-[60%] md:w-[30%] flex items-center gap-3">
        {currentTrack ? (
          <>
            <img 
              src={currentTrack.artwork?.['150x150'] || currentTrack.artwork?.['480x480'] || 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=150'} 
              onError={(e) => { e.currentTarget.src = 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=150'; }}
              alt={currentTrack.title} 
              className="w-10 h-10 md:w-14 md:h-14 rounded-md shadow-lg object-cover"
            />
            <div className="flex flex-col">
              <span className="text-sm font-semibold hover:underline cursor-pointer line-clamp-1">{currentTrack.title}</span>
              <span className="text-[10px] md:text-xs text-gray-400 hover:underline cursor-pointer line-clamp-1">{currentTrack.user?.name || 'Unknown Artist'}</span>
            </div>
          </>
        ) : (
          <div className="text-xs text-gray-400 pl-2">No track</div>
        )}
      </div>

      {/* Center: Controls */}
      <div className="flex-1 md:w-[40%] flex flex-col items-center justify-center gap-2">
        <div className="flex items-center gap-4 md:gap-6">
          <button 
            onClick={toggleShuffle} 
            className={`hidden md:block transition-colors disabled:opacity-50 ${isShuffling ? 'text-indigo-500' : 'text-gray-400 hover:text-white'}`}
            disabled={!currentTrack}
          >
            <Shuffle size={18} />
          </button>

          <button 
            onClick={prevTrack} 
            className="hidden md:block text-gray-400 hover:text-white transition-colors disabled:opacity-50"
            disabled={!currentTrack}
          >
            <SkipBack size={20} fill="currentColor" />
          </button>
          
          <button 
            onClick={togglePlayPause} 
            className="w-8 h-8 md:w-9 md:h-9 rounded-full bg-white text-black flex items-center justify-center hover:scale-105 transition-transform disabled:opacity-50 md:shadow-[0_0_15px_rgba(255,255,255,0.3)]"
            disabled={!currentTrack}
          >
            {isPlaying ? <Pause size={16} fill="currentColor" /> : <Play size={16} fill="currentColor" className="ml-1" />}
          </button>
          
          <button 
            onClick={nextTrack} 
            className="text-gray-400 hover:text-white transition-colors disabled:opacity-50"
            disabled={!currentTrack}
          >
            <SkipForward size={20} fill="currentColor" />
          </button>
          
          <button 
            onClick={toggleRepeat} 
            className={`hidden md:block transition-colors disabled:opacity-50 ${isRepeating ? 'text-indigo-500' : 'text-gray-400 hover:text-white'}`}
            disabled={!currentTrack}
          >
            <Repeat size={18} />
          </button>
        </div>

        <div className="hidden md:flex w-full max-w-md items-center gap-2 text-xs text-gray-400">
          <span className="w-8 text-right block">{formatTime(currentSeconds)}</span>
          <input 
            type="range"
            min="0"
            max="1"
            step="0.001"
            value={Number.isFinite(progress) ? progress : 0}
            onChange={(e) => handleSeek(parseFloat(e.target.value))}
            className="flex-1 h-1.5 accent-white hover:accent-indigo-500 cursor-pointer bg-gray-600/50 rounded-full"
          />
          <span className="w-8 block">{formatTime(duration)}</span>
        </div>
      </div>

      {/* Right: Volume & Extra */}
      <div className="hidden md:flex w-[30%] items-center justify-end gap-3 pr-2">
        <MonitorSpeaker size={16} className="text-gray-400 hover:text-white cursor-pointer" />
        <Volume2 size={16} className="text-gray-400 hover:text-white cursor-pointer" />
        <input 
          type="range"
          min="0"
          max="1"
          step="0.01"
          value={Number.isFinite(volume) ? volume : 1}
          onChange={(e) => setVolume(parseFloat(e.target.value))}
          className="w-24 h-1.5 accent-white hover:accent-indigo-500 cursor-pointer bg-gray-600/50 rounded-full"
        />
      </div>

      {/* Mobile Progress Bar absolute at bottom */}
      <div className="md:hidden absolute bottom-0 left-2 right-2 h-[2px] bg-gray-800 rounded-full overflow-hidden pointer-events-none">
        <div 
          className="h-full bg-white relative"
          style={{ width: `${(Number.isFinite(progress) ? progress : 0) * 100}%` }}
        ></div>
      </div>
    </div>
  );
}
