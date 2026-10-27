import { Resend } from 'resend';
import { config } from '../config/env';

export interface SendOtpResult {
  sent: boolean;
  deliveredToInbox?: boolean;
  reason?: string;
}

let resendClient: Resend | null = null;

function getResendClient(): Resend | null {
  if (resendClient) return resendClient;
  const apiKey = config.email?.resendApiKey || process.env.RESEND_API_KEY;
  if (apiKey) {
    resendClient = new Resend(apiKey);
  }
  return resendClient;
}

/**
 * Sends transactional email via Brevo HTTPS REST API (Port 443).
 * Supports delivery to ANY recipient email worldwide without custom domain restrictions.
 */
async function sendViaBrevo(
  to: string,
  subject: string,
  htmlContent: string
): Promise<{ sent: boolean; messageId?: string; reason?: string }> {
  const apiKey = config.email?.brevoApiKey || process.env.BREVO_API_KEY;
  if (!apiKey) {
    return { sent: false, reason: 'Brevo API key not configured' };
  }

  try {
    const fromAddress = config.email?.fromEmail || 'nebula.app.auth@gmail.com';
    // Parse sender name & email if formatted like "Nebula <email>"
    const match = fromAddress.match(/^(.*?)\s*<(.+?)>$/);
    const senderName = match ? match[1].trim() : 'Nebula';
    const senderEmail = match ? match[2].trim() : fromAddress;

    const response = await fetch('https://api.brevo.com/v3/smtp/email', {
      method: 'POST',
      headers: {
        'api-key': apiKey,
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify({
        sender: {
          name: senderName,
          email: senderEmail,
        },
        to: [
          {
            email: to,
          },
        ],
        subject,
        htmlContent,
      }),
    });

    const responseData = (await response.json()) as any;

    if (!response.ok) {
      console.warn(`[EMAIL SERVICE] Brevo HTTP error (${response.status}):`, responseData?.message || responseData);
      return {
        sent: false,
        reason: responseData?.message || `Brevo returned HTTP ${response.status}`,
      };
    }

    console.log(`[EMAIL SERVICE] ✉️ Dispatched email to ${to} via Brevo (MessageId: ${responseData?.messageId})`);
    return {
      sent: true,
      messageId: responseData?.messageId,
    };
  } catch (err: any) {
    console.warn(`[EMAIL SERVICE] Brevo request failed:`, err.message);
    return {
      sent: false,
      reason: err.message,
    };
  }
}

/**
 * Sends a 6-digit OTP verification email directly to the recipient inbox.
 * Attempts Brevo first (unrestricted recipient delivery to any Gmail).
 * Falls back to Resend if Brevo encounters an error.
 */
export async function sendOtpEmail(to: string, code: string): Promise<SendOtpResult> {
  const html = `
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8">
      <style>
        body { margin: 0; padding: 0; background-color: #0b0c10; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; color: #f0f2fa; }
        .container { max-width: 460px; margin: 30px auto; background: #13151f; border: 1px solid #25283b; border-radius: 16px; padding: 32px 28px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
        .logo { font-size: 24px; font-weight: 800; letter-spacing: 4px; color: #8b5cf6; text-align: center; margin-bottom: 6px; }
        .subtitle { font-size: 13px; color: #828aa0; text-align: center; margin-bottom: 28px; }
        .otp-box { background: #1b1e2c; border: 1px dashed #6366f1; border-radius: 12px; padding: 18px; text-align: center; margin: 24px 0; }
        .otp-code { font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #a78bfa; font-family: monospace; }
        .text { font-size: 14px; line-height: 1.6; color: #d0d5e5; }
        .footer { margin-top: 28px; font-size: 12px; color: #636b83; text-align: center; border-top: 1px solid #202334; padding-top: 18px; }
      </style>
    </head>
    <body>
      <div class="container">
        <div class="logo">NEBULA</div>
        <div class="subtitle">Synchronized Watch Parties & Shared Spaces</div>
        <p class="text">Hello,</p>
        <p class="text">Use the 6-digit verification code below to access your account:</p>
        <div class="otp-box">
          <span class="otp-code">${code}</span>
        </div>
        <p class="text" style="font-size: 12px; color: #94a3b8;">This code will expire in <strong>10 minutes</strong>. Do not share this code with anyone.</p>
        <div class="footer">
          If you didn't request this login code, you can safely disregard this email.
        </div>
      </div>
    </body>
    </html>
  `;
  const subject = `Your Nebula Verification Code: ${code}`;

  // 1. Primary: Try Brevo (Port 443 HTTPS - supports ANY recipient email)
  const brevoResult = await sendViaBrevo(to, subject, html);
  if (brevoResult.sent) {
    return { sent: true, deliveredToInbox: true };
  }

  // 2. Secondary: Fall back to Resend (Port 443 HTTPS)
  const resend = getResendClient();
  if (resend) {
    try {
      const fromAddress = 'Nebula <onboarding@resend.dev>';
      const { data, error } = await resend.emails.send({
        from: fromAddress,
        to: [to],
        subject,
        html,
      });

      if (!error) {
        console.log(`[NEBULA AUTH] ✉️ Successfully dispatched OTP email to ${to} via Resend fallback (ID: ${data?.id})`);
        return { sent: true, deliveredToInbox: true };
      }
      console.warn(`[NEBULA AUTH] Resend failed for ${to}:`, error.message);
    } catch (err: any) {
      console.warn(`[NEBULA AUTH] Resend exception for ${to}:`, err.message);
    }
  }

  if (process.env.NODE_ENV === 'test') {
    return { sent: true, deliveredToInbox: false };
  }

  return {
    sent: false,
    reason: brevoResult.reason || 'All email dispatch providers failed',
  };
}

/**
 * Sends automated update / notification email ONLY if the user has emailUpdates enabled in their profile.
 * Retains absolute user sovereignty over automated email dispatch.
 */
export async function sendAutomatedNotificationEmail(
  user: { email?: string; notificationPrefs?: { emailUpdates?: boolean } },
  subject: string,
  html: string
): Promise<SendOtpResult> {
  if (!user.email) {
    return { sent: false, reason: 'no_email' };
  }

  // Strict cloud-level check on notification preferences
  if (user.notificationPrefs?.emailUpdates !== true) {
    console.log(`[Email Service] Automated email suppressed for ${user.email}: user has emailUpdates disabled in settings.`);
    return { sent: false, reason: 'email_updates_disabled_by_user' };
  }

  const brevoResult = await sendViaBrevo(user.email, subject, html);
  if (brevoResult.sent) {
    return { sent: true, deliveredToInbox: true };
  }

  const resend = getResendClient();
  if (resend) {
    try {
      const fromAddress = 'Nebula <onboarding@resend.dev>';
      const { data, error } = await resend.emails.send({
        from: fromAddress,
        to: [user.email],
        subject,
        html,
      });

      if (!error) {
        console.log(`[Email Service] Dispatched automated email to ${user.email} via Resend (ID: ${data?.id})`);
        return { sent: true, deliveredToInbox: true };
      }
      return { sent: false, reason: error.message };
    } catch (err: any) {
      console.warn('[Email Service] Resend automated notification error:', err.message);
      return { sent: false, reason: err.message };
    }
  }

  return { sent: false, reason: 'no_email_transporter' };
}

/**
 * Dispatches an emergency security alert directly to the administrator.
 * Bypasses user notification preferences because this is a mission-critical infrastructure alert.
 */
export async function sendEmergencySecurityAlert(
  adminEmail: string,
  incident: {
    reason: string;
    ip: string;
    method: string;
    path: string;
    query: any;
    bodySnippet: string;
    userAgent: string;
    timestamp: string;
  }
): Promise<SendOtpResult> {
  const subject = `🚨 CRITICAL SECURITY ALERT: Nebula Backend Frozen / Lockdown Engaged (${incident.reason.slice(0, 40)})`;
  const html = `
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8">
      <style>
        body { margin: 0; padding: 0; background-color: #0b0c10; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; color: #f0f2fa; }
        .container { max-width: 580px; margin: 30px auto; background: #181119; border: 2px solid #ef4444; border-radius: 16px; padding: 32px 28px; }
        .badge { background: #ef4444; color: #ffffff; padding: 4px 10px; border-radius: 6px; font-weight: 800; font-size: 12px; letter-spacing: 1px; display: inline-block; margin-bottom: 12px; }
        .title { font-size: 20px; font-weight: 800; color: #fca5a5; margin-bottom: 8px; }
        .desc { font-size: 14px; color: #fecaca; line-height: 1.5; margin-bottom: 20px; }
        .card { background: #0e070c; border: 1px solid #7f1d1d; border-radius: 10px; padding: 16px; font-family: monospace; font-size: 13px; line-height: 1.6; color: #f87171; overflow-x: auto; }
        .row { margin-bottom: 8px; }
        .key { color: #fca5a5; font-weight: 700; }
        .footer { margin-top: 24px; font-size: 12px; color: #991b1b; text-align: center; }
      </style>
    </head>
    <body>
      <div class="container">
        <div class="badge">EMERGENCY TRIPWIRE ACTIVATED</div>
        <div class="title">Nebula Backend Circuit Breaker Engaged</div>
        <p class="desc">
          An illegal access or database tampering probe was detected. The backend has <strong>frozen all incoming traffic</strong> (0 requests accepted) to preserve system integrity.
        </p>
        <div class="card">
          <div class="row"><span class="key">Incident Reason:</span> ${incident.reason}</div>
          <div class="row"><span class="key">Attacker IP:</span> ${incident.ip}</div>
          <div class="row"><span class="key">Request:</span> ${incident.method} ${incident.path}</div>
          <div class="row"><span class="key">User-Agent:</span> ${incident.userAgent}</div>
          <div class="row"><span class="key">Query:</span> ${JSON.stringify(incident.query)}</div>
          <div class="row"><span class="key">Body Snippet:</span> ${incident.bodySnippet}</div>
          <div class="row"><span class="key">Timestamp:</span> ${incident.timestamp}</div>
        </div>
        <p class="footer">
          To safely restore services, trigger <code>POST /api/v1/security/admin-unlock</code> with your admin key.
        </p>
      </div>
    </body>
    </html>
  `;

  const brevoResult = await sendViaBrevo(adminEmail, subject, html);
  if (brevoResult.sent) {
    return { sent: true, deliveredToInbox: true };
  }

  const resend = getResendClient();
  if (resend) {
    try {
      const fromAddress = 'Nebula Security <onboarding@resend.dev>';
      const { data, error } = await resend.emails.send({
        from: fromAddress,
        to: [adminEmail],
        subject,
        html,
      });

      if (!error) {
        console.log(`[SECURITY ALERT] Dispatched emergency incident alert to ${adminEmail} via Resend (ID: ${data?.id})`);
        return { sent: true, deliveredToInbox: true };
      }
      return { sent: false, reason: error.message };
    } catch (err: any) {
      console.warn('[SECURITY ALERT] Resend alert dispatch failed:', err.message);
      return { sent: false, reason: err.message };
    }
  }

  return { sent: false, reason: 'no_email_transporter' };
}
