import React, { createContext, useContext, useEffect, useRef, useState, ReactNode } from 'react';
import { audiusService, type Track } from '../services/AudiusService';

interface PlayerContextType {
  currentTrack: Track | null;
  isPlaying: boolean;
  duration: number; // in seconds
  volume: number; // 0 to 1
  queue: Track[];
  isShuffling: boolean;
  isRepeating: boolean;
  audioRef: React.RefObject<HTMLAudioElement | null>;
  playTrack: (track: Track, newQueue?: Track[]) => void;
  togglePlayPause: () => void;
  nextTrack: () => void;
  prevTrack: () => void;
  seek: (progress: number) => void;
  setVolume: (volume: number) => void;
  toggleShuffle: () => void;
  toggleRepeat: () => void;
}

export const PlayerContext = createContext<PlayerContextType | null>(null);

export function PlayerProvider({ children }: { children: ReactNode }) {
  const [currentTrack, setCurrentTrack] = useState<Track | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [duration, setDuration] = useState(0);
  const [volume, setVolumeState] = useState(() => {
    return Number(localStorage.getItem('orion_volume')) || 1;
  });
  const [queue, setQueue] = useState<Track[]>([]);
  const [isShuffling, setIsShuffling] = useState(false);
  const [isRepeating, setIsRepeating] = useState(false);

  const toggleShuffle = () => setIsShuffling(!isShuffling);
  const toggleRepeat = () => setIsRepeating(!isRepeating);
  
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const currentTrackIdRef = useRef<string | null>(null);

  useEffect(() => {
    if (!audioRef.current) {
      audioRef.current = new Audio();
      audioRef.current.volume = volume;
    }
  }, []);

  const playTrack = async (track: Track, newQueue?: Track[]) => {
    if (newQueue) setQueue(newQueue);
    setCurrentTrack(track);
    currentTrackIdRef.current = track.id;
    setIsPlaying(true);
    setDuration(track.duration);

    if (audioRef.current) {
      try {
        const streamUrl = await audiusService.getStreamUrl(track.id);
        if (currentTrackIdRef.current !== track.id) return; // Prevent race conditions
        
        audioRef.current.src = streamUrl;
        const playPromise = audioRef.current.play();
        if (playPromise !== undefined) {
          playPromise.catch(error => {
            console.warn("Playback interrupted:", error);
          });
        }
      } catch (error) {
        console.warn("Failed to get stream url:", error);
      }
    }
  };

  const togglePlayPause = () => {
    if (!audioRef.current || !currentTrack) return;
    if (isPlaying) {
      audioRef.current.pause();
      setIsPlaying(false);
    } else {
      const playPromise = audioRef.current.play();
      if (playPromise !== undefined) {
        playPromise.then(() => {
          setIsPlaying(true);
        }).catch(error => {
          console.warn("Playback interrupted:", error);
        });
      } else {
        setIsPlaying(true);
      }
    }
  };

  const nextTrack = () => {
    if (!currentTrack || queue.length === 0) return;
    
    if (isRepeating && audioRef.current) {
      audioRef.current.currentTime = 0;
      const playPromise = audioRef.current.play();
      if (playPromise !== undefined) {
        playPromise.catch(error => console.warn("Playback interrupted:", error));
      }
      return;
    }

    if (isShuffling) {
      let nextIdx = Math.floor(Math.random() * queue.length);
      if (queue.length > 1 && queue[nextIdx].id === currentTrack.id) {
         nextIdx = (nextIdx + 1) % queue.length;
      }
      playTrack(queue[nextIdx], queue);
      return;
    }

    const currentIndex = queue.findIndex(t => t.id === currentTrack.id);
    if (currentIndex !== -1 && currentIndex < queue.length - 1) {
      playTrack(queue[currentIndex + 1], queue);
    }
  };

  const prevTrack = () => {
    if (audioRef.current && audioRef.current.currentTime > 3) {
      // If playing for more than 3s, restart track
      seek(0);
    } else {
      if (isRepeating && audioRef.current) {
        seek(0);
        return;
      }
      const currentIndex = queue.findIndex(t => t.id === currentTrack?.id);
      if (currentIndex > 0) {
        playTrack(queue[currentIndex - 1], queue);
      }
    }
  };

  const seek = (newProgress: number) => {
    if (audioRef.current && duration > 0) {
      audioRef.current.currentTime = newProgress * duration;
    }
  };

  const setVolume = (newVolume: number) => {
    setVolumeState(newVolume);
    localStorage.setItem('orion_volume', newVolume.toString());
    if (audioRef.current) {
      audioRef.current.volume = newVolume;
    }
  };

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    const handleEnded = () => {
      nextTrack();
    };

    audio.addEventListener('ended', handleEnded);

    return () => {
      audio.removeEventListener('ended', handleEnded);
    };
  }, [duration, queue, currentTrack, isRepeating, isShuffling]);

  return (
    <PlayerContext.Provider
      value={{
        currentTrack,
        isPlaying,
        duration,
        volume,
        queue,
        isShuffling,
        isRepeating,
        audioRef,
        playTrack,
        togglePlayPause,
        nextTrack,
        prevTrack,
        seek,
        setVolume,
        toggleShuffle,
        toggleRepeat
      }}
    >
      {children}
    </PlayerContext.Provider>
  );
}
