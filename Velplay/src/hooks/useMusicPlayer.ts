import { useContext } from 'react';
import { PlayerContext } from '../contexts/PlayerContext';

export function useMusicPlayer() {
  const context = useContext(PlayerContext);
  if (!context) {
    throw new Error('useMusicPlayer must be used within a PlayerProvider');
  }
  return context;
}
