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

// Global atomic circuit breaker state (strictly controlled by authorized administrators)
let isServiceLockedDown = false;
let activeIncident: SecurityIncident | null = null;

// Attacker IP quarantine list (IP -> expiry timestamp)
const quarantinedIps = new Map<string, number>();

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

function extractClientIp(req: Request): string {
  return (req.headers['x-forwarded-for'] as string)?.split(',')[0].trim() || req.socket.remoteAddress || 'unknown';
}

/**
 * Quarantines a malicious attacker IP for 24 hours and dispatches an incident forensic alert.
 * DOES NOT crash or freeze the service for legitimate users.
 */
export async function quarantineAttacker(req: Request, reason: string): Promise<SecurityIncident> {
  const ip = extractClientIp(req);
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

  // Quarantine attacker IP for 24 hours
  quarantinedIps.set(ip, Date.now() + 24 * 60 * 60 * 1000);
  activeIncident = incident;

  console.warn('================================================================');
  console.warn('🛡️ [ATTACKER IP QUARANTINED] Automated Honeypot / Exploit Defense');
  console.warn(`  REASON: ${incident.reason}`);
  console.warn(`  ATTACKER IP: ${incident.ip}`);
  console.warn(`  REQUEST: ${incident.method} ${incident.path}`);
  console.warn(`  TIME: ${incident.timestamp}`);
  console.warn('  ACTION: Banned attacker IP for 24 hours. Legitimate traffic continues unaffected.');
  console.warn('================================================================');

  try {
    await sendEmergencySecurityAlert(ADMIN_ALERT_EMAIL, incident);
  } catch (err: any) {
    console.error('[SECURITY ALERT] Error dispatching incident email:', err.message);
  }

  return incident;
}

/**
 * Triggers an immediate emergency freeze of the entire backend (manual administrative command).
 */
export function tripSecurityLockdown(incident: SecurityIncident): void {
  isServiceLockedDown = true;
  activeIncident = incident;

  console.error('================================================================');
  console.error('🚨 [SECURITY CIRCUIT BREAKER TRIPPED] MANUAL EMERGENCY LOCKDOWN ENGAGED');
  console.error(`  REASON: ${incident.reason}`);
  console.error(`  TRIGGERED BY: ${incident.ip}`);
  console.error(`  TIME: ${incident.timestamp}`);
  console.error('  ACTION: Backend is now FREEZING all incoming traffic (0 requests accepted).');
  console.error('================================================================');
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

export function isIpQuarantined(ip: string): boolean {
  const expiresAt = quarantinedIps.get(ip);
  if (!expiresAt) return false;
  if (Date.now() > expiresAt) {
    quarantinedIps.delete(ip);
    return false;
  }
  return true;
}

/**
 * Express middleware that guards every incoming request:
 * 1. Checks if the request is an admin lock/unlock command.
 * 2. Checks if global circuit breaker is active -> rejects with HTTP 503.
 * 3. Checks if client IP is quarantined -> rejects with HTTP 403.
 * 4. Inspects path & queries for honeypots/malicious payloads -> bans attacker IP immediately.
 */
export async function securityLockdownMiddleware(req: Request, res: Response, next: NextFunction): Promise<void> {
  // Allow health/readiness probes to avoid Docker/orchestrator crash-loops
  if (req.path === '/health' || req.path === '/ready') {
    return next();
  }

  const clientIp = extractClientIp(req);

  // 1. Admin unlock endpoint
  if (req.path === '/api/v1/security/admin-unlock' && req.method === 'POST') {
    const providedSecret = req.headers['x-admin-key'] || req.body?.secret;
    if (providedSecret === ADMIN_UNLOCK_TOKEN) {
      resetSecurityLockdown();
      quarantinedIps.delete(clientIp);
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

  // Admin manual lockdown endpoint
  if (req.path === '/api/v1/security/admin-lockdown' && req.method === 'POST') {
    const providedSecret = req.headers['x-admin-key'] || req.body?.secret;
    if (providedSecret === ADMIN_UNLOCK_TOKEN) {
      tripSecurityLockdown({
        reason: req.body?.reason || 'Administrative manual emergency freeze',
        ip: clientIp,
        method: req.method,
        path: req.path,
        query: req.query,
        bodySnippet: 'none',
        userAgent: (req.headers['user-agent'] as string) || 'unknown',
        timestamp: new Date().toISOString(),
      });
      res.status(200).json({
        success: true,
        message: 'Emergency security lockdown engaged.',
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
      isIpQuarantined: isIpQuarantined(clientIp),
    });
    return;
  }

  // 2. If global service is manually locked down, block traffic with 503
  if (isServiceLockedDown) {
    res.status(503).json({
      success: false,
      error: 'CRITICAL_SECURITY_LOCKDOWN',
      message: 'Emergency maintenance active: Nebula backend is temporarily frozen for scheduled security maintenance.',
    });
    return;
  }

  // 3. If attacker IP is quarantined, reject with 403 Forbidden
  if (isIpQuarantined(clientIp)) {
    res.status(403).json({
      success: false,
      error: 'IP_QUARANTINED',
      message: 'Access denied: Your IP address has been temporarily banned due to suspicious activity.',
    });
    return;
  }

  // 4. Inspect URL for Honeypot traps
  const path = req.path;
  for (const pattern of HONEYPOT_PATTERNS) {
    if (pattern.test(path)) {
      await quarantineAttacker(req, `Honeypot probe access attempted: ${path}`);
      res.status(403).json({
        success: false,
        error: 'ACCESS_DENIED',
        message: 'Forbidden: Probing restricted administrative resources is logged and banned.',
      });
      return;
    }
  }

  // 5. Inspect query string for injection / exploit attempts
  const originalUrl = req.originalUrl || '';
  for (const pattern of EXPLOIT_QUERY_PATTERNS) {
    if (pattern.test(originalUrl)) {
      await quarantineAttacker(req, `Exploit pattern detected in request URL: ${pattern}`);
      res.status(403).json({
        success: false,
        error: 'ACCESS_DENIED',
        message: 'Forbidden: Exploit signatures are rejected and origin IP has been banned.',
      });
      return;
    }
  }

  next();
}
