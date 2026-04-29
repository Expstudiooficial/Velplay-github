export interface Track {
  id: string;
  title: string;
  duration: number; // in seconds
  artwork: {
    '150x150': string;
    '480x480': string;
    '1000x1000': string;
  };
  user: {
    name: string;
    handle: string;
  };
  genre: string;
  play_count: number;
}

export interface Playlist {
  id: string;
  playlist_name: string;
  artwork: {
    '150x150': string;
    '480x480': string;
    '1000x1000': string;
  };
}

class AudiusService {
  private host: string | null = null;
  private appName = 'ProjectOrion';

  async getHost(): Promise<string> {
    if (this.host) return this.host;
    
    try {
      const sample = (arr: string[]) => arr[Math.floor(Math.random() * arr.length)];
      const res = await fetch('https://api.audius.co');
      const data = await res.json();
      
      const nodes: string[] = data.data;
      
      // Ping nodes to find the fastest
      let fastestNode = nodes[0];
      // Due to potential API limits on client, we just sample 3 random nodes and ping them
      const sampleNodes = [sample(nodes), sample(nodes), sample(nodes)];
      
      let minPing = Infinity;
      for (const node of sampleNodes) {
        const start = Date.now();
        try {
          const controller = new AbortController();
          const timeoutId = setTimeout(() => controller.abort(), 1000);
          await fetch(`${node}/health_check`, { method: 'HEAD', signal: controller.signal });
          clearTimeout(timeoutId);
          const ping = Date.now() - start;
          if (ping < minPing) {
            minPing = ping;
            fastestNode = node;
          }
        } catch (e) {
          console.warn(`Node ${node} timeout/error`);
        }
      }
      
      this.host = fastestNode;
      return fastestNode;
    } catch (error) {
      console.error('Failed to get Audius host', error);
      return 'https://discoveryprovider.audius.co'; // Fallback
    }
  }

  async fetchWithFailover(path: string, options: RequestInit = {}): Promise<any> {
    const host = await this.getHost();
    const separator = path.includes('?') ? '&' : '?';
    const url = `${host}${path}${separator}app_name=${this.appName}`;
    
    try {
      const res = await fetch(url, options);
      if (!res.ok) throw new Error('API Error');
      return await res.json();
    } catch (error) {
      console.warn(`Fetch failed for ${url}, rotating host...`);
      this.host = null; // Invalidate host
      const newHost = await this.getHost();
      const newUrl = `${newHost}${path}${separator}app_name=${this.appName}`;
      const newRes = await fetch(newUrl, options);
      if (!newRes.ok) throw new Error('API Error on failover');
      return await newRes.json();
    }
  }

  async getTrendingTracks(limit = 100, offset = 0): Promise<Track[]> {
    const data = await this.fetchWithFailover(`/v1/tracks/trending?limit=${limit}&offset=${offset}`);
    return data.data;
  }

  async searchTracks(query: string, limit = 100, offset = 0): Promise<Track[]> {
    const data = await this.fetchWithFailover(`/v1/tracks/search?query=${encodeURIComponent(query)}&limit=${limit}&offset=${offset}`);
    return data.data;
  }
  
  async getStreamUrl(trackId: string): Promise<string> {
     const host = await this.getHost();
     return `${host}/v1/tracks/${trackId}/stream?app_name=${this.appName}`;
  }
}

export const audiusService = new AudiusService();
