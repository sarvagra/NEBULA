import { Request, Response, NextFunction } from 'express';

function containsNoSqlInjection(obj: any): boolean {
  if (!obj || typeof obj !== 'object') return false;

  for (const key of Object.keys(obj)) {
    // Check for MongoDB operator injection or prototype pollution
    if (key.startsWith('$') || key.includes('.') || key === '__proto__' || key === 'constructor' || key === 'prototype') {
      return true;
    }

    if (typeof obj[key] === 'object' && containsNoSqlInjection(obj[key])) {
      return true;
    }
  }

  return false;
}

export function noSqlSanitizer(req: Request, res: Response, next: NextFunction): void {
  if (req.body && containsNoSqlInjection(req.body)) {
    res.status(400).json({ success: false, error: 'Malicious operator detected in request body' });
    return;
  }

  if (req.query && containsNoSqlInjection(req.query)) {
    res.status(400).json({ success: false, error: 'Malicious operator detected in query parameters' });
    return;
  }

  if (req.params && containsNoSqlInjection(req.params)) {
    res.status(400).json({ success: false, error: 'Malicious operator detected in path parameters' });
    return;
  }

  next();
}
