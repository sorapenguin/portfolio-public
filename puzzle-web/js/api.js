import { API_BASE } from './config.js';
import { getToken, logout } from './auth.js';

export async function fetchWeb(path, options = {}) {
  const token = getToken();
  const headers = { ...(options.headers || {}) };
  const hasBody = options.body !== undefined && options.body !== null;
  if (hasBody && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  if (token) headers['X-Puzzle-Token'] = token;

  const res = await fetch(API_BASE + path, { ...options, headers });
  if (res.status === 401) {
    logout();
    location.href = '/login.html';
    return undefined;
  }
  return res;
}

export async function fetchJson(path, options = {}) {
  const res = await fetchWeb(path, options);
  if (!res) return undefined;
  if (!res.ok) throw new Error(`${path} failed: ${res.status}`);
  if (res.status === 204) return null;
  return res.json();
}

