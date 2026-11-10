const BASE_URL = '/api/integrations/github';

export async function beginGitHubConnection() {
  const response = await fetch(`${BASE_URL}/connect`, {
    method: 'POST',
    credentials: 'include',
  });
  if (response.status === 401 || response.status === 403) {
    throw new Error('Your DevPilot session expired. Sign in again and retry.');
  }
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || typeof payload.installationUrl !== 'string') {
    throw new Error('Could not start the GitHub connection. Check the GitHub App setup and try again.');
  }
  return payload.installationUrl;
}

export async function getGitHubIntegration() {
  const response = await fetch(BASE_URL, { credentials: 'include' });
  if (response.status === 401 || response.status === 403) {
    return { authenticated: false };
  }
  if (!response.ok) throw new Error('Could not load GitHub connection status. Please try again.');
  return { authenticated: true, integration: await response.json() };
}

export async function getGitHubRepositories() {
  const response = await fetch(`${BASE_URL}/repositories`, { credentials: 'include' });
  if (response.status === 401 || response.status === 403) throw new Error('Sign in again to view GitHub repositories.');
  if (!response.ok) throw new Error('Could not load repositories from GitHub. Please retry.');
  return response.json();
}

export async function setGitHubRepositoryAgentAccess(repositoryId, enabled) {
  const response = await fetch(`${BASE_URL}/repositories/${encodeURIComponent(repositoryId)}/access`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  });
  if (!response.ok) throw new Error('Could not update this repository’s DevPilot access. Please retry.');
  return response.json();
}

export async function disconnectGitHub() {
  const response = await fetch(`${BASE_URL}/disconnect`, {
    method: 'POST',
    credentials: 'include',
  });
  if (response.status === 401 || response.status === 403) {
    throw new Error('Your DevPilot session expired. Sign in again and retry.');
  }
  if (!response.ok) throw new Error('Could not disconnect GitHub. Please try again.');
}
