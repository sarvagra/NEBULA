import { Request, Response, NextFunction } from 'express';
import { ZodError } from 'zod';
import { config } from '../config/env';

export function errorHandler(
  err: any,
  req: Request,
  res: Response,
  next: NextFunction
): void {
  // Always log detailed stack trace to server-side stderr for debugging
  console.error(`[Error] ${req.method} ${req.originalUrl}:`, err);

  if (err instanceof ZodError) {
    res.status(400).json({
      success: false,
      error: 'Validation failed',
      details: err.errors.map((e) => ({ field: e.path.join('.'), message: e.message })),
    });
    return;
  }

  // Handle Payload Too Large (e.g. from body-parser)
  if (err.type === 'entity.too.large' || err.status === 413) {
    res.status(413).json({
      success: false,
      error: 'Payload Too Large: Request body exceeds maximum allowed size (100KB)',
    });
    return;
  }

  // Handle MongoDB / Mongoose errors
  if (err.name === 'CastError') {
    res.status(400).json({
      success: false,
      error: 'Invalid resource identifier format',
    });
    return;
  }

  if (err.code === 11000) {
    res.status(409).json({
      success: false,
      error: 'A resource with this unique value already exists',
    });
    return;
  }

  const statusCode = typeof err.statusCode === 'number' ? err.statusCode : 500;

  // In production, never leak internal error messages or stacks
  const safeMessage = config.server.isProduction && statusCode === 500
    ? 'An unexpected internal server error occurred. Please try again later.'
    : err.message || 'Internal server error';

  res.status(statusCode).json({
    success: false,
    error: safeMessage,
  });
}
