import mongoose, { Schema, Document } from 'mongoose';

export type MessageType = 'text' | 'media' | 'system';

export interface IMessage extends Document {
  roomId?: string;
  conversationId?: string;
  senderId: string;
  senderName: string;
  senderAvatarLetter: string;
  senderAvatarUrl?: string;
  text: string;
  mediaUrl?: string;
  mediaUrls?: string[];
  isSpoiler: boolean;
  mediaMimeType?: string;
  messageType: MessageType;
  reactions: { emoji: string; count: number; userIds: string[] }[];
  /** Direct messages only.  The recipient explicitly marks these read. */
  readAt?: Date;
  deliveredAt?: Date;
  editedAt?: Date;
  editHistory: { text: string; editedAt: Date }[];
  deletedAt?: Date;
  /**
   * N-003: client-supplied idempotency key. Prevents duplicate DMs on retry/
   * reconnect. Scoped per sender+conversation pair so the key never needs to be
   * globally unique — the client can use a UUID or timestamp per message.
   * Stored only for DMs (conversationId present, roomId absent).
   */
  idempotencyKey?: string;
  createdAt: Date;
}

const MessageSchema = new Schema<IMessage>(
  {
    roomId: { type: String, index: true },
    conversationId: { type: String, index: true },
    senderId: { type: String, required: true, index: true },
    senderName: { type: String, required: true },
    senderAvatarLetter: { type: String, default: 'U' },
    senderAvatarUrl: { type: String },
    text: { type: String, default: '' },
    mediaUrl: { type: String },
    // One message may carry a pile of photos; mediaUrl stays the first for older clients.
    mediaUrls: [{ type: String }],
    isSpoiler: { type: Boolean, default: false },
    mediaMimeType: { type: String },
    // 'room_card' is a shared watch-party invite: mediaUrl holds nebula://room/<code>.
    messageType: { type: String, enum: ['text', 'media', 'video', 'system', 'room_card'], default: 'text' },
    reactions: [
      {
        emoji: { type: String, required: true },
        count: { type: Number, default: 0 },
        userIds: { type: [String], default: [] },
      },
    ],
    readAt: { type: Date },
    deliveredAt: { type: Date },
    editedAt: { type: Date },
    editHistory: [{
      text: { type: String, required: true },
      editedAt: { type: Date, required: true },
    }],
    deletedAt: { type: Date },
    // N-003: deduplication. Declared as optional String with no default.
    // A pre-validate hook (below) strips null/empty so the partial-filter index
    // only ever sees real, non-empty string values and null never reaches it.
    idempotencyKey: { type: String },
  },
  {
    timestamps: { createdAt: true, updatedAt: false },
    toJSON: {
      virtuals: true,
      transform: (_, ret: any) => {
        ret.id = ret._id.toString();
        delete ret._id;
        delete ret.__v;
        return ret;
      },
    },
  }
);

// N-003: strip null/empty idempotencyKey before save. When no key is supplied
// Mongoose stores null for declared String paths. By unsetting it here the field
// is truly absent in the stored document, so the partialFilterExpression below
// excludes it from the unique index and the E11000 cannot fire for un-keyed msgs.
MessageSchema.pre('save', function (next) {
  if (this.idempotencyKey == null || this.idempotencyKey === '') {
    this.idempotencyKey = undefined;
  }
  next();
});

// N-003: deduplication index. Unique per sender+conversation+key.
// partialFilterExpression restricts the index to documents that actually have a
// non-empty idempotencyKey — null/absent docs are excluded at the MongoDB level,
// so the uniqueness constraint never fires for un-keyed messages.
MessageSchema.index(
  { senderId: 1, conversationId: 1, idempotencyKey: 1 },
  {
    unique: true,
    name: 'dm_idempotency',
    partialFilterExpression: { idempotencyKey: { $type: 'string' } },
  }
);

export const MessageModel = mongoose.model<IMessage>('Message', MessageSchema);
