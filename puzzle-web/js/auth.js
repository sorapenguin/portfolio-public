import { API_BASE } from './config.js';

const TOKEN_KEY = 'puzzle_token';
const USERNAME_KEY = 'puzzle_username';

function saveSession(data) {
  const token = data?.token ?? data?.accessToken ?? '';
  const username = data?.username ?? data?.name ?? '';
  if (!token) throw new Error('Token was not returned');
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USERNAME_KEY, username);
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function getUsername() {
  return localStorage.getItem(USERNAME_KEY) || '';
}

export function logout() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USERNAME_KEY);
}

export function requireAuth() {
  if (!getToken()) {
    location.href = '/login.html';
    return false;
  }
  return true;
}

export async function initOnFirstVisit() {
  if (getToken()) return { token: getToken(), username: getUsername() };
  const res = await fetch(`${API_BASE}/auth/init`);
  if (!res.ok) throw new Error(`init failed: ${res.status}`);
  const data = await res.json();
  saveSession(data);
  return data;
}

export async function loginWithUsername(username) {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username }),
  });
  if (!res.ok) throw new Error(`login failed: ${res.status}`);
  const data = await res.json();
  saveSession(data);
  return data;
}

export function applyHeaderUser() {
  const userEl = document.querySelector('[data-username]');
  if (userEl) userEl.textContent = getUsername();
  document.querySelectorAll('[data-logout]').forEach((button) => {
    button.addEventListener('click', () => {
      logout();
      location.href = '/login.html';
    });
  });
}

