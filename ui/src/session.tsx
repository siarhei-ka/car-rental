import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { useNavigate } from "react-router";
import { ApiError, SIGNED_OUT_EVENT, customerApi, gateway, unwrap } from "./api/clients";
import type { components } from "./api/generated/customer";

export type Profile = components["schemas"]["Profile"];

/** Who is using the UI. Visitors can search without signing in. */
export type Session =
  | { state: "loading" }
  | { state: "signed-out" }
  /** Signed in with an account that is not a customer account, such as an administrator. */
  | { state: "not-customer"; name: string }
  | { state: "customer"; name: string };

/** Leaves the page. Tests replace it. */
export const browser = {
  go: (url: string) => globalThis.location.assign(url),
};

export const SIGN_IN_URL = "/oauth2/authorization/keycloak";

/** Where to go after the sign-in, kept while the browser visits Keycloak. */
const RETURN_KEY = "ui:after-sign-in";

/** Signs in through Keycloak, then comes back to {@code returnTo}. */
export function signIn(returnTo: string) {
  sessionStorage.setItem(RETURN_KEY, returnTo);
  browser.go(SIGN_IN_URL);
}

export async function signOut() {
  const { redirectTo } = await unwrap(gateway.POST("/logout"));
  browser.go(redirectTo);
}

export function useSession(): Session {
  const queryClient = useQueryClient();
  const session = useQuery({
    queryKey: ["session"],
    queryFn: () => unwrap(gateway.GET("/api/v1/session")),
    staleTime: Infinity,
  });

  useEffect(() => {
    const refresh = () => void queryClient.invalidateQueries({ queryKey: ["session"] });
    globalThis.addEventListener(SIGNED_OUT_EVENT, refresh);
    return () => globalThis.removeEventListener(SIGNED_OUT_EVENT, refresh);
  }, [queryClient]);

  if (session.isPending) {
    return { state: "loading" };
  }
  if (session.isError || !session.data.signedIn) {
    return { state: "signed-out" };
  }
  const name = session.data.name ?? "";
  return session.data.roles.includes("CUSTOMER")
    ? { state: "customer", name }
    : { state: "not-customer", name };
}

/** The driver profile of the signed-in customer, or null before they have entered it. */
export function useProfile(enabled: boolean) {
  return useQuery({
    queryKey: ["profile"],
    enabled,
    queryFn: async (): Promise<Profile | null> => {
      try {
        return await unwrap(customerApi.GET("/api/v1/profile"));
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return null;
        }
        throw error;
      }
    },
  });
}

/** The address of the profile page that returns to {@code next} once the profile is saved. */
export function profileUrl(next: string) {
  return `/profile?next=${encodeURIComponent(next)}`;
}

/**
 * After a sign-in, returns to the page where it started. A customer without a driver profile is
 * asked for it first, as on the first sign-in.
 */
export function AfterSignIn() {
  const session = useSession();
  const navigate = useNavigate();
  const returnTo = sessionStorage.getItem(RETURN_KEY);
  const profile = useProfile(session.state === "customer");

  useEffect(() => {
    if (!returnTo || session.state === "loading") {
      return;
    }
    if (session.state !== "customer") {
      sessionStorage.removeItem(RETURN_KEY);
      return;
    }
    if (profile.isPending) {
      return;
    }
    sessionStorage.removeItem(RETURN_KEY);
    void navigate(profile.data === null ? profileUrl(returnTo) : returnTo, { replace: true });
  }, [returnTo, session.state, profile.isPending, profile.data, navigate]);

  return null;
}
