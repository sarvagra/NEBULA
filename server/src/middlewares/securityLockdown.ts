import { Request, Response, NextFunction } from 'express';
import { sendEmergencySecurityAlert } from '../services/emailService';

export interface SecurityIncident {
  reason: string;
  ip: string;
  method: string;
  path: string;
  query: any;
  bodySnippet: string;
  userAgent: string;
  timestamp: string;
}

// Global atomic circuit breaker state
let isServiceLockedDown = false;
let activeIncident: SecurityIncident | null = null;

// Admin override key (fallback to secure default if env var unset)
const ADMIN_UNLOCK_TOKEN = process.env.ADMIN_UNLOCK_SECRET || 'nebula_emergency_unlock_2026';
const ADMIN_ALERT_EMAIL = process.env.SECURITY_ALERT_EMAIL || 'sarvagrawork@gmail.com';

// Honeypot route patterns that no legitimate client or UI should ever call
const HONEYPOT_PATTERNS = [
  /^\/\.env/i,
  /^\/\.git/i,
  /^\/\.aws/i,
  /^\/id_rsa/i,
  /^\/wp-admin/i,
  /^\/phpmyadmin/i,
  /^\/admin\/db/i,
  /^\/admin\/database/i,
  /^\/dump/i,
  /^\/_mongo/i,
  /^\/debug\/vars/i,
  /^\/actuator/i,
  /^\/etc\/passwd/i,
  /\.\.\//, // Directory traversal
  /\.\.%2f/i,
];

// Dangerous SQLi, command injection or NoSQL exploit probes in URL or query
const EXPLOIT_QUERY_PATTERNS = [
  /union\s+select/i,
  /exec\s*\(/i,
  /sleep\(\s*\d+\s*\)/i,
  /\$where/i,
  /\$regex.*system/i,
  /;\s*rm\s+-rf/i,
  /\|\s*bash/i,
  /`.*curl.*`/i,
];

/**
 * Triggers an immediate emergency freeze of the entire backend.
 * Dispatches a high-priority alert email to the admin with full incident forensics.
 */
export async function tripSecurityLockdown(req: Request, reason: string): Promise<void> {
  const ip = (req.headers['x-forwarded-for'] as string)?.split(',')[0].trim() || req.socket.remoteAddress || 'unknown';
  const incident: SecurityIncident = {
    reason,
    ip,
    method: req.method,
    path: req.originalUrl || req.path,
    query: req.query,
    bodySnippet: req.body ? JSON.stringify(req.body).slice(0, 500) : 'none',
    userAgent: (req.headers['user-agent'] as string) || 'unknown',
    timestamp: new Date().toISOString(),
  };

  if (!isServiceLockedDown) {
    isServiceLockedDown = true;
    activeIncident = incident;

    console.error('================================================================');
    console.error('🚨 [SECURITY CIRCUIT BREAKER TRIPPED] EMERGENCY LOCKDOWN ENGAGED');
    console.error(`  REASON: ${incident.reason}`);
    console.error(`  ATTACKER IP: ${incident.ip}`);
    console.error(`  REQUEST: ${incident.method} ${incident.path}`);
    console.error(`  TIME: ${incident.timestamp}`);
    console.error('  ACTION: Backend is now FREEZING all incoming traffic (0 requests accepted).');
    console.error('================================================================');

    // Asynchronously dispatch urgent alert email to administrator
    try {
      await sendEmergencySecurityAlert(ADMIN_ALERT_EMAIL, incident);
    } catch (err: any) {
      console.error('[SECURITY ALERT] Error dispatching incident email:', err.message);
    }
  }
}

/**
 * Resets the circuit breaker and restores normal traffic processing.
 */
export function resetSecurityLockdown(): void {
  isServiceLockedDown = false;
  activeIncident = null;
  console.log('[SECURITY CIRCUIT BREAKER] Service unlocked by authorized administrator.');
}

export function isLockedDown(): boolean {
  return isServiceLockedDown;
}

export function getActiveIncident(): SecurityIncident | null {
  return activeIncident;
}

/**
 * Express middleware that guards every incoming request:
 * 1. Checks if the request is an admin unlock command.
 * 2. Checks if circuit breaker is active -> rejects with HTTP 503.
 * 3. Inspects path & queries for honeypots/malicious payloads -> trips lockdown if detected.
 */
export async function securityLockdownMiddleware(req: Request, res: Response, next: NextFunction): Promise<void> {
  // Allow health/readiness probes to avoid Docker/orchestrator crash-loops
  if (req.path === '/health' || req.path === '/ready') {
    return next();
  }

  // 1. Admin unlock endpoint
  if (req.path === '/api/v1/security/admin-unlock' && req.method === 'POST') {
    const providedSecret = req.headers['x-admin-key'] || req.body?.secret;
    if (providedSecret === ADMIN_UNLOCK_TOKEN) {
      resetSecurityLockdown();
      res.status(200).json({
        success: true,
        message: 'Security lockdown successfully released. Service restored to normal operations.',
      });
      return;
    } else {
      res.status(403).json({
        success: false,
        error: 'Invalid administrator authorization key.',
      });
      return;
    }
  }

  // Admin status check endpoint
  if (req.path === '/api/v1/security/status' && req.method === 'GET') {
    res.status(200).json({
      success: true,
      lockedDown: isServiceLockedDown,
      incident: activeIncident,
    });
    return;
  }

  // 2. If already locked down, block all traffic immediately
  if (isServiceLockedDown) {
    res.status(503).json({
      success: false,
      error: 'CRITICAL_SECURITY_LOCKDOWN',
      message: 'Emergency security lockdown active: Service is temporarily frozen due to suspicious activity. Administrative notification dispatched.',
    });
    return;
  }

  // 3. Inspect URL for Honeypot traps
  const path = req.path;
  for (const pattern of HONEYPOT_PATTERNS) {
    if (pattern.test(path)) {
      await tripSecurityLockdown(req, `Honeypot probe access attempted: ${path}`);
      res.status(503).json({
        success: false,
        error: 'CRITICAL_SECURITY_LOCKDOWN',
        message: 'Emergency security lockdown active: Access to forbidden resource triggered instant service freeze.',
      });
      return;
    }
  }

  // 4. Inspect query string for injection / exploit attempts
  const originalUrl = req.originalUrl || '';
  for (const pattern of EXPLOIT_QUERY_PATTERNS) {
    if (pattern.test(originalUrl)) {
      await tripSecurityLockdown(req, `Exploit pattern detected in request URL: ${pattern}`);
      res.status(503).json({
        success: false,
        error: 'CRITICAL_SECURITY_LOCKDOWN',
        message: 'Emergency security lockdown active: Exploit attempt triggered instant service freeze.',
      });
      return;
    }
  }

  next();
}
