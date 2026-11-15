import { createContext, useContext, useState, type ReactNode } from "react";

const STORAGE_KEY = "admin-ui.country";

type CountryState = {
  country: string | null;
  setCountry: (code: string) => void;
  /** Whether the administrator may choose the country. A country administrator may not. */
  choosable: boolean;
};

const CountryContext = createContext<CountryState | null>(null);

/**
 * The country the administrator works in. A super administrator chooses it, and the choice is
 * remembered in the browser. A country administrator always works in their own country.
 */
export function CountryProvider({
  children,
  fixed,
}: {
  children: ReactNode;
  fixed: string | null;
}) {
  const [chosen, setState] = useState<string | null>(() => localStorage.getItem(STORAGE_KEY));
  const setCountry = (code: string) => {
    localStorage.setItem(STORAGE_KEY, code);
    setState(code);
  };
  const value: CountryState = fixed
    ? { country: fixed, setCountry: () => {}, choosable: false }
    : { country: chosen, setCountry, choosable: true };
  return <CountryContext value={value}>{children}</CountryContext>;
}

export function useCountry(): CountryState {
  const state = useContext(CountryContext);
  if (!state) {
    throw new Error("useCountry must be used inside CountryProvider");
  }
  return state;
}
