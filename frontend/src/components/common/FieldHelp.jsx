import React from 'react';

export function FieldHelp({ children }) {
  if (!children) return null;
  return <p className="field-help-text">{children}</p>;
}
