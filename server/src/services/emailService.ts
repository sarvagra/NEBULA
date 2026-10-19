import nodemailer, { Transporter } from 'nodemailer';
import { Resend } from 'resend';
import { config } from '../config/env';

interface SendOtpResult {
  sent: boolean;
  reason?: string;
}

let transporter: Transporter | null = null;
let resendClient: Resend | null = null;

function getResendClient(): Resend | null {
  if (resendClient) return resendClient;
  if (config.email.resendApiKey) {
    resendClient = new Resend(config.email.resendApiKey);
  }
  return resendClient;
}

function getTransporter(): Transporter | null {
  if (transporter) return transporter;

  const { smtpHost, smtpPort, smtpSecure, smtpUser, smtpPass } = config.email;

  if (!smtpUser && !smtpHost) {
    return null;
  }

  try {
    if (smtpUser && smtpUser.endsWith('@gmail.com') && !smtpHost) {
      transporter = nodemailer.createTransport({
        service: 'gmail',
        auth: {
          user: smtpUser,
          pass: smtpPass,
        },
      });
    } else if (smtpHost) {
      transporter = nodemailer.createTransport({
        host: smtpHost,
        port: smtpPort,
        secure: smtpSecure || smtpPort === 465,
        auth: smtpUser && smtpPass ? {
          user: smtpUser,
          pass: smtpPass,
        } : undefined,
      });
    }
  } catch (err: any) {
    console.error('[EMAIL SERVICE] Failed to initialize nodemailer transporter:', err.message);
    transporter = null;
  }

  return transporter;
}

export async function sendOtpEmail(to: string, code: string): Promise<SendOtpResult> {
  // 1. Try Resend if configured
  const resend = getResendClient();
  if (resend) {
    try {
      const fromAddress = config.email.fromEmail || 'Nebula <onboarding@resend.dev>';
      const { data, error } = await resend.emails.send({
        from: fromAddress,
        to: [to],
        subject: `Your Nebula Verification Code: ${code}`,
        html: `
          <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0c0d14; color: #f0f2fa; padding: 32px 24px; border-radius: 14px; max-width: 480px; margin: auto;">
            <div style="text-align: center; margin-bottom: 24px;">
              <h1 style="color: #8b5cf6; margin: 0; font-size: 26px; letter-spacing: 3px;">NEBULA</h1>
              <p style="color: #94a3b8; font-size: 13px; margin-top: 4px;">Synchronized Media & Shared Spaces</p>
            </div>
            <p style="font-size: 15px; margin-bottom: 8px;">Hello,</p>
            <p style="font-size: 14px; color: #cbd5e1; line-height: 1.5;">Use the 6-digit verification code below to access your account:</p>
            <div style="background: #181926; border: 1px dashed #6366f1; border-radius: 12px; padding: 18px; text-align: center; margin: 24px 0;">
              <span style="font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #a78bfa; font-family: monospace;">${code}</span>
            </div>
            <p style="font-size: 12px; color: #64748b; line-height: 1.5;">This code will expire in <strong>10 minutes</strong>. If you did not request this login code, you can safely ignore this email.</p>
          </div>
        `,
      });

      if (error) {
        console.error('[NEBULA AUTH] ⚠️ Resend dispatch error:', error);
      } else {
        console.log(`[NEBULA AUTH] ✉️ Successfully dispatched OTP email to ${to} via Resend (ID: ${data?.id})`);
        return { sent: true };
      }
    } catch (err: any) {
      console.error('[NEBULA AUTH] ⚠️ Resend network exception:', err.message);
    }
  }

  // 2. Try Nodemailer (SMTP / Gmail App Password)
  const mailer = getTransporter();

  if (!mailer) {
    console.log(`\n================================================================`);
    console.log(`[NEBULA AUTH] ✉️  OTP email could not be dispatched to ${to}`);
    console.log(`  STATUS: SMTP and Resend are not configured.`);
    console.log(`================================================================\n`);
    return {
      sent: false,
      reason: 'No SMTP credentials or RESEND_API_KEY set in server/.env',
    };
  }

  try {
    const fromAddress = config.email.fromEmail || config.email.smtpUser || 'no-reply@nebula.app';

    const mailOptions = {
      from: fromAddress,
      to,
      subject: `Your Nebula Verification Code: ${code}`,
      text: `Your Nebula verification code is: ${code}\n\nThis code is valid for 10 minutes.\nIf you did not request this code, you can safely ignore this email.`,
      html: `
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
            <p class="text">Use the 6-digit verification code below to sign into your Nebula account:</p>
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
      `,
    };

    const info = await mailer.sendMail(mailOptions);
    console.log(`[NEBULA AUTH] ✉️ Successfully sent OTP email to ${to} (MessageID: ${info.messageId})`);
    return {
      sent: true,
    };
  } catch (err: any) {
    console.error(`[NEBULA AUTH] ⚠️ Failed to send OTP email to ${to}:`, err.message);
    return {
      sent: false,
      reason: err.message,
    };
  }
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

  const resend = getResendClient();
  if (resend) {
    try {
      const fromAddress = config.email.fromEmail || 'Nebula <onboarding@resend.dev>';
      const { data, error } = await resend.emails.send({
        from: fromAddress,
        to: [user.email],
        subject,
        html,
      });

      if (!error) {
        console.log(`[Email Service] Dispatched automated email to ${user.email} (ID: ${data?.id})`);
        return { sent: true };
      }
    } catch (err: any) {
      console.warn('[Email Service] Resend error:', err.message);
    }
  }

  const mailer = getTransporter();
  if (mailer) {
    try {
      await mailer.sendMail({
        from: config.email.fromEmail || 'no-reply@nebula.app',
        to: user.email,
        subject,
        html,
      });
      return { sent: true };
    } catch (err: any) {
      return { sent: false, reason: err.message };
    }
  }

  return { sent: false, reason: 'no_email_transporter' };
}

