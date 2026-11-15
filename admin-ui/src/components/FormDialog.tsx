import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import type { FormEvent, ReactNode } from "react";
import { ErrorAlert } from "./ErrorAlert";

type Props = {
  title: string;
  open: boolean;
  saving: boolean;
  error: unknown;
  onClose: () => void;
  onSubmit: () => void;
  children: ReactNode;
};

/** A form in a dialog. On a phone it fills the screen. */
export function FormDialog({ title, open, saving, error, onClose, onSubmit, children }: Props) {
  const theme = useTheme();
  const phone = useMediaQuery(theme.breakpoints.down("sm"));
  const submit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit();
  };
  return (
    <Dialog open={open} onClose={onClose} fullScreen={phone} fullWidth maxWidth="sm">
      <form onSubmit={submit} noValidate>
        <DialogTitle>{title}</DialogTitle>
        <DialogContent>
          <ErrorAlert error={error} />
          <Stack spacing={2} sx={{ pt: 1 }}>
            {children}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} sx={{ minHeight: 44 }}>
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={saving} sx={{ minHeight: 44 }}>
            Save
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
