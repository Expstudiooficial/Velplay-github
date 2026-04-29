import React, { useState, useEffect } from 'react';
import { supabase } from '../lib/supabase';

export function SupabaseSetupWarning() {
  const [closed, setClosed] = useState(false);
  const [show, setShow] = useState(false);

  useEffect(() => {
    const handler = () => setShow(true);
    window.addEventListener('supabase_missing_tables', handler);
    return () => window.removeEventListener('supabase_missing_tables', handler);
  }, []);

  if (closed || !show) return null;

  const sql = `
-- Run this in your Supabase SQL Editor
create table public.liked_tracks (
  id uuid default gen_random_uuid() primary key,
  user_id uuid references auth.users not null,
  trackId text not null,
  trackData jsonb not null,
  likedAt bigint not null
);

create table public.playlists (
  id uuid default gen_random_uuid() primary key,
  user_id uuid references auth.users not null,
  name text not null,
  createdAt bigint not null
);

create table public.playlist_tracks (
  id uuid default gen_random_uuid() primary key,
  playlistId uuid references public.playlists on delete cascade not null,
  trackId text not null,
  trackData jsonb not null,
  addedAt bigint not null
);

-- Enable RLS
alter table public.liked_tracks enable row level security;
alter table public.playlists enable row level security;
alter table public.playlist_tracks enable row level security;

-- Policies
create policy "Users can see their own liked tracks" on public.liked_tracks for select using (auth.uid() = user_id);
create policy "Users can insert their own liked tracks" on public.liked_tracks for insert with check (auth.uid() = user_id);
create policy "Users can delete their own liked tracks" on public.liked_tracks for delete using (auth.uid() = user_id);

create policy "Users can see their own playlists" on public.playlists for select using (auth.uid() = user_id);
create policy "Users can insert their own playlists" on public.playlists for insert with check (auth.uid() = user_id);
create policy "Users can delete their own playlists" on public.playlists for delete using (auth.uid() = user_id);

create policy "Users can see their own playlist tracks" on public.playlist_tracks for select using (
  exists (select 1 from public.playlists where id = playlistId and user_id = auth.uid())
);
create policy "Users can insert their own playlist tracks" on public.playlist_tracks for insert with check (
  exists (select 1 from public.playlists where id = playlistId and user_id = auth.uid())
);
create policy "Users can delete their own playlist tracks" on public.playlist_tracks for delete using (
  exists (select 1 from public.playlists where id = playlistId and user_id = auth.uid())
);

-- Force schema cache reload (Resolves PGRST205 errors)
NOTIFY pgrst, 'reload schema';
  `;

  return (
    <div className="fixed bottom-4 left-4 right-4 bg-orange-900 border border-orange-500 rounded p-4 text-orange-100 z-50">
      <div className="flex justify-between items-start mb-2">
        <h3 className="font-bold">Supabase Setup Required</h3>
        <button onClick={() => setClosed(true)} className="text-orange-300 hover:text-white">✕</button>
      </div>
      <p className="text-sm mb-4">Please run this SQL in your Supabase SQL Editor to enable Cloud Sync and clear any caching errors (like PGRST205):</p>
      <pre className="text-xs bg-black/50 p-2 rounded max-h-48 overflow-y-auto font-mono text-gray-300">
        {sql}
      </pre>
    </div>
  );
}
