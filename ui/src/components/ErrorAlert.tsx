import { Alert } from "@mui/material";

/** Shows the explanation of a failed request. */
export function ErrorAlert({ error }: { error: unknown }) {
  if (!error) {
    return null;
  }
  const message = error instanceof Error ? error.message : "Something went wrong";
  return (
    <Alert severity="error" sx={{ mb: 2 }}>
      {message}
    </Alert>
  );
}
