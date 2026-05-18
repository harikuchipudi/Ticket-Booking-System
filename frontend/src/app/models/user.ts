export interface User {
  userId: string;
  email: string;
  displayName: string;
}

export interface AuthRequest {
  email: string;
  password: string;
  displayName?: string;
}

export interface AuthResponse {
  token: string;
  userId: string;
  displayName: string;
  expiresIn: number;
}
