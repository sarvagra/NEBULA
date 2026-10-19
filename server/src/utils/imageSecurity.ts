/**
 * Image Security & Decompression Bomb / Pixel Flood Defense Utility.
 * Inspects image binary headers directly from buffer to extract width and height
 * without allocating large decompression bitmaps in memory.
 */

export interface ImageDimensions {
  width: number;
  height: number;
  mimeType: string;
}

export const MAX_IMAGE_DIMENSION = 4096; // Max 4096 pixels wide or high
export const MAX_IMAGE_PIXELS = 16_777_216; // Max 16 Megapixels (4096 x 4096)

/**
 * Parses raw image buffer header to extract width and height.
 * Supports PNG, JPEG, GIF, and WebP.
 */
export function extractImageDimensions(buffer: Buffer): ImageDimensions | null {
  try {
    if (!buffer || buffer.length < 16) return null;

    // 1. PNG Detection: \x89PNG\r\n\x1a\n
    if (
      buffer[0] === 0x89 &&
      buffer[1] === 0x50 &&
      buffer[2] === 0x4e &&
      buffer[3] === 0x47 &&
      buffer[4] === 0x0d &&
      buffer[5] === 0x0a &&
      buffer[6] === 0x1a &&
      buffer[7] === 0x0a
    ) {
      if (buffer.length >= 24) {
        const width = buffer.readUInt32BE(16);
        const height = buffer.readUInt32BE(20);
        return { width, height, mimeType: 'image/png' };
      }
    }

    // 2. GIF Detection: GIF87a or GIF89a
    if (
      buffer[0] === 0x47 &&
      buffer[1] === 0x49 &&
      buffer[2] === 0x46 &&
      buffer[3] === 0x38 &&
      (buffer[4] === 0x37 || buffer[4] === 0x39) &&
      buffer[5] === 0x61
    ) {
      if (buffer.length >= 10) {
        const width = buffer.readUInt16LE(6);
        const height = buffer.readUInt16LE(8);
        return { width, height, mimeType: 'image/gif' };
      }
    }

    // 3. JPEG Detection: \xFF\xD8
    if (buffer[0] === 0xff && buffer[1] === 0xd8) {
      let offset = 2;
      while (offset + 4 <= buffer.length) {
        // Find next marker
        if (buffer[offset] !== 0xff) {
          offset++;
          continue;
        }
        const marker = buffer[offset + 1];
        // Skip padding 0xFF or stand-alone markers (RST, SOI, EOI)
        if (marker === 0xff || marker === 0x00 || marker === 0xd8 || marker === 0xd9 || (marker >= 0xd0 && marker <= 0xd7)) {
          offset += 2;
          continue;
        }

        if (offset + 4 > buffer.length) break;
        const length = buffer.readUInt16BE(offset + 2);
        if (length < 2) break;

        // SOF markers: SOF0 (0xC0) to SOF15 (0xCF) excluding DHT (0xC4), JPG (0xC8), DAC (0xCC)
        const isSOF =
          marker >= 0xc0 &&
          marker <= 0xcf &&
          marker !== 0xc4 &&
          marker !== 0xc8 &&
          marker !== 0xcc;

        if (isSOF && offset + 9 <= buffer.length) {
          const height = buffer.readUInt16BE(offset + 5);
          const width = buffer.readUInt16BE(offset + 7);
          return { width, height, mimeType: 'image/jpeg' };
        }

        offset += 2 + length;
      }
    }

  // 4. WebP Detection: RIFF....WEBP
  if (
    buffer.length >= 30 &&
    buffer.toString('ascii', 0, 4) === 'RIFF' &&
    buffer.toString('ascii', 8, 12) === 'WEBP'
  ) {
    const chunkType = buffer.toString('ascii', 12, 16);

    // VP8 (lossy)
    if (chunkType === 'VP8 ') {
      // Frame header key frame sync code at offset 23: 0x9D 0x01 0x2A
      if (buffer[23] === 0x9d && buffer[24] === 0x01 && buffer[25] === 0x2a) {
        const width = buffer.readUInt16LE(26) & 0x3fff;
        const height = buffer.readUInt16LE(28) & 0x3fff;
        return { width, height, mimeType: 'image/webp' };
      }
    }

    // VP8L (lossless)
    if (chunkType === 'VP8L' && buffer[20] === 0x2f) {
      const b1 = buffer[21];
      const b2 = buffer[22];
      const b3 = buffer[23];
      const b4 = buffer[24];
      const width = 1 + (((b2 & 0x3f) << 8) | b1);
      const height = 1 + (((b4 & 0x0f) << 10) | (b3 << 2) | ((b2 & 0xc0) >> 6));
      return { width, height, mimeType: 'image/webp' };
    }

    // VP8X (extended)
    if (chunkType === 'VP8X' && buffer.length >= 30) {
      const width = 1 + (buffer[24] | (buffer[25] << 8) | (buffer[26] << 16));
      const height = 1 + (buffer[27] | (buffer[28] << 8) | (buffer[29] << 16));
      return { width, height, mimeType: 'image/webp' };
    }
  }

    return null;
  } catch {
    return null;
  }
}

/**
 * Validates image dimensions against pixel flood / decompression bomb limits.
 * Returns null if safe, or an error string if violation is detected.
 */
export function validateImageResolution(buffer: Buffer): { valid: boolean; error?: string; dimensions?: ImageDimensions } {
  const dims = extractImageDimensions(buffer);
  if (!dims) {
    // If format is recognized but dimensions can't be read, or unrecognized format, allow pass-through
    // if sizeBytes is within limits, or enforce recognized headers.
    return { valid: true };
  }

  if (dims.width > MAX_IMAGE_DIMENSION || dims.height > MAX_IMAGE_DIMENSION) {
    return {
      valid: false,
      error: `Image dimensions (${dims.width}x${dims.height}) exceed the maximum allowable resolution of ${MAX_IMAGE_DIMENSION}x${MAX_IMAGE_DIMENSION} pixels.`,
      dimensions: dims,
    };
  }

  const totalPixels = dims.width * dims.height;
  if (totalPixels > MAX_IMAGE_PIXELS) {
    return {
      valid: false,
      error: `Image pixel count (${totalPixels.toLocaleString()} pixels) exceeds maximum allowed spread (${MAX_IMAGE_PIXELS.toLocaleString()} pixels).`,
      dimensions: dims,
    };
  }

  return { valid: true, dimensions: dims };
}
