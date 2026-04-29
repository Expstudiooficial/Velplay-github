import { createClient } from '@supabase/supabase-js';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY;

if (!supabaseAnonKey) {
  console.warn("VITE_SUPABASE_ANON_KEY is missing. Supabase functionality will not work.");
}

export const supabase = createClient(supabaseUrl || 'https://eexnzitsurxqtwcocjzy.supabase.co', supabaseAnonKey || 'sb_publishable_SgqZ_cHbosALS44ByZ5_jA_neEgXJet');
