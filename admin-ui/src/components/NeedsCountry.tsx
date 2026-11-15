import { Alert } from "@mui/material";
import type { ReactNode } from "react";
import { useCountry } from "../country";

/** Shows its content only after a country is chosen in the top bar. */
export function NeedsCountry({ children }: { children: (country: string) => ReactNode }) {
  const { country } = useCountry();
  if (!country) {
    return <Alert severity="info">Choose a country in the top bar first.</Alert>;
  }
  return <>{children(country)}</>;
}
