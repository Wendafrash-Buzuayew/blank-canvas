import { ApiError } from './api';

/**
 * Convert transport/technical errors into calm, user-facing copy.
 * Never surface raw status codes, SQL constraints or UUIDs to end users.
 */
export function friendlyError(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (err instanceof ApiError) {
    const raw = (err.message || '').toLowerCase();

    if (raw.includes('foreign key') || raw.includes('constraint')) {
      return 'That selection is no longer available. Please refresh and choose another option.';
    }
    if (raw.includes('uuid') || raw.includes('cast') || raw.includes('deserialize')) {
      return 'We could not process that selection. Please pick it again from the list.';
    }

    switch (err.status) {
      case 400:
        return 'Some details are missing or invalid. Please review the form and try again.';
      case 401:
        return 'Your session has expired. Please sign in again.';
      case 403:
        return 'You do not have permission to do that.';
      case 404:
        return 'That item no longer exists. It may have been removed by someone else.';
      case 409:
        return 'This already exists. Please use a different name or value.';
      case 422:
        return 'Please check the highlighted fields and try again.';
      case 503:
        return 'The service is temporarily unavailable. Please try again in a moment.';
      default:
        break;
    }

    // Backend-provided business messages are already human readable.
    if (err.message && !/^request failed/i.test(err.message)) return err.message;
    return fallback;
  }

  if (err instanceof Error && err.message.toLowerCase().includes('failed to fetch')) {
    return 'We could not reach the server. Check your connection and try again.';
  }

  return fallback;
}
