import { useState, useEffect } from 'react';

export function useHashRoute(defaultHash = '#/describe') {
  const [hash, setHash] = useState(() => window.location.hash || defaultHash);

  useEffect(() => {
    const onHashChange = () => setHash(window.location.hash || defaultHash);
    window.addEventListener('hashchange', onHashChange);
    if (!window.location.hash) {
      window.location.hash = defaultHash;
    }
    return () => window.removeEventListener('hashchange', onHashChange);
  }, [defaultHash]);

  const navigate = (newHash) => {
    window.location.hash = newHash;
  };

  return [hash, navigate];
}
