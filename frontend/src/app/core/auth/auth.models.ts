export type UserRole = 'OPERATOR' | 'AUDITOR';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  role: UserRole;
}
