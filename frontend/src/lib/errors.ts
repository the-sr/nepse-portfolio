import axios from 'axios';

export function errorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; detail?: string; error?: string } | undefined;
    if (data?.message) return data.message;
    if (data?.detail) return data.detail;
    if (data?.error) return data.error;
    if (error.code === 'ERR_NETWORK') return 'Cannot reach the API. Is the backend running?';
    return error.message;
  }
  if (error instanceof Error) return error.message;
  return fallback;
}
