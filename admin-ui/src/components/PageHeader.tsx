import { Button, Stack, Typography } from "@mui/material";

type Props = { title: string; action?: { label: string; onClick: () => void; disabled?: boolean } };

export function PageHeader({ title, action }: Props) {
  return (
    <Stack
      direction={{ xs: "column", sm: "row" }}
      spacing={1}
      sx={{ mb: 2, justifyContent: "space-between", alignItems: { sm: "center" } }}
    >
      <Typography variant="h5" component="h2">
        {title}
      </Typography>
      {action && (
        <Button
          variant="contained"
          onClick={action.onClick}
          disabled={action.disabled}
          sx={{ minHeight: 44 }}
        >
          {action.label}
        </Button>
      )}
    </Stack>
  );
}
