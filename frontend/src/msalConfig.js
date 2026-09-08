import { LogLevel } from '@azure/msal-browser';

const getRedirectUri = () => {
  if (typeof window === 'undefined') return '';
  const full = window.location.origin + window.location.pathname;
  return full.endsWith('/') ? full : full + '/';
};

export const msalConfig = {
  auth: {
    clientId: import.meta.env.VITE_AZURE_CLIENT_ID || '00000000-0000-0000-0000-000000000000',
    authority: `https://login.microsoftonline.com/${import.meta.env.VITE_AZURE_TENANT_ID || 'common'}`,
    redirectUri: getRedirectUri(),
    postLogoutRedirectUri: getRedirectUri(),
  },
  cache: {
    cacheLocation: 'sessionStorage',
    storeAuthStateInCookie: false,
  },
  system: {
    loggerOptions: {
      loggerCallback: (level, message, containsPii) => {
        if (containsPii) return;
        if (level === LogLevel.Error) console.error(message);
        if (level === LogLevel.Info) console.info(message);
      },
      logLevel: LogLevel.Info,
      piiLoggingEnabled: false,
    },
  },
};

export const loginRequest = {
  scopes: ['openid', 'profile', 'User.Read'],
};
