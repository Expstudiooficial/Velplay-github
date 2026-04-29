import { Track } from '../services/AudiusService';
import { supabase } from './supabase';
import { useState, useEffect } from 'react';

export interface LikedTrack {
  id?: string;
  user_id?: string;
  trackId: string;
  trackData: Track;
  likedAt: number;
}

export interface CustomPlaylist {
  id?: string;
  user_id?: string;
  name: string;
  createdAt: number;
}

export interface PlaylistTrack {
  id?: string;
  playlistId: string;
  trackId: string;
  trackData: Track;
  addedAt: number;
}

// Data fetching hooks for Supabase

export function useLikedTracks(user: any) {
  const [likedTracks, setLikedTracks] = useState<LikedTrack[]>([]);

  useEffect(() => {
    if (!user) {
      setLikedTracks([]);
      return;
    }
    const fetchLikes = async () => {
      const { data, error } = await supabase.from('liked_tracks').select('*').eq('user_id', user.id).order('likedAt', { ascending: false });
      if (error && (error.code === '42P01' || error.code === 'PGRST205')) {
        window.dispatchEvent(new Event('supabase_missing_tables'));
      }
      if (data) setLikedTracks(data as LikedTrack[]);
    };
    fetchLikes();

    const channel = supabase.channel(`liked_tracks_changes_${user.id}_${Math.random()}`)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'liked_tracks', filter: `user_id=eq.${user.id}` }, () => {
        fetchLikes();
      }).subscribe();

    return () => { supabase.removeChannel(channel); };
  }, [user]);

  return likedTracks;
}

export function usePlaylists(user: any) {
  const [playlists, setPlaylists] = useState<CustomPlaylist[]>([]);

  useEffect(() => {
    if (!user) {
      setPlaylists([]);
      return;
    }
    const fetchPlaylists = async () => {
      const { data, error } = await supabase.from('playlists').select('*').eq('user_id', user.id).order('createdAt', { ascending: true });
      if (error && (error.code === '42P01' || error.code === 'PGRST205')) {
        window.dispatchEvent(new Event('supabase_missing_tables'));
      }
      if (data) setPlaylists(data as CustomPlaylist[]);
    };
    fetchPlaylists();

    const channel = supabase.channel(`playlists_changes_${user.id}_${Math.random()}`)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'playlists', filter: `user_id=eq.${user.id}` }, () => {
        fetchPlaylists();
      }).subscribe();

    return () => { supabase.removeChannel(channel); };
  }, [user]);

  return playlists;
}

export function usePlaylistTracks(user: any, playlistId: string | null) {
  const [tracks, setTracks] = useState<PlaylistTrack[]>([]);

  useEffect(() => {
    if (!user || !playlistId) {
      setTracks([]);
      return;
    }
    const fetchTracks = async () => {
      const { data, error } = await supabase.from('playlist_tracks').select('*').eq('playlistId', playlistId).order('addedAt', { ascending: true });
      if (data) setTracks(data as PlaylistTrack[]);
    };
    fetchTracks();

    const channel = supabase.channel(`playlist_tracks_changes_${playlistId}_${Math.random()}`)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'playlist_tracks', filter: `playlistId=eq.${playlistId}` }, () => {
        fetchTracks();
      }).subscribe();

    return () => { supabase.removeChannel(channel); };
  }, [user, playlistId]);

  return tracks;
}

export const exportDB = async () => {
  // Can be ignored or simplified for remote db
  console.log("Export DB is disabled for Supabase");
  alert("Export DB is disabled for Supabase");
}