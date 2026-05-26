import { describe, it, expect, vi, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useForm, FormProvider } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { axe } from 'vitest-axe';
import { toHaveNoViolations } from 'vitest-axe/matchers';
import { FormErrorSummary } from '../form-error-summary';
import { FormField } from '../form-field';
import { Input } from '@/components/ui/Input';

expect.extend({ toHaveNoViolations });

beforeAll(() => {
  Element.prototype.scrollIntoView = vi.fn();
});

const testSchema = z.object({
  username: z.string().min(1, 'Il nome utente è obbligatorio'),
  email: z.string().min(1, "L'email è obbligatoria").email('Email non valida'),
});

type TestFormValues = z.infer<typeof testSchema>;

const FIELD_LABELS: Record<string, string> = {
  username: 'Nome utente',
  email: 'Email',
};

function TestForm({ onSubmit = vi.fn() }: { onSubmit?: () => void }) {
  const methods = useForm<TestFormValues>({
    resolver: zodResolver(testSchema),
    mode: 'onSubmit',
  });

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = methods;

  return (
    <FormProvider {...methods}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormErrorSummary errors={errors} fieldLabels={FIELD_LABELS} />

        <FormField name="username" label="Nome utente" error={errors.username?.message}>
          <Input
            id="username"
            aria-describedby={errors.username ? 'username-error' : undefined}
            {...register('username')}
          />
        </FormField>

        <FormField name="email" label="Email" error={errors.email?.message}>
          <Input
            id="email"
            type="email"
            aria-describedby={errors.email ? 'email-error' : undefined}
            {...register('email')}
          />
        </FormField>

        <button type="submit">Invia</button>
      </form>
    </FormProvider>
  );
}

describe('FormErrorSummary + FormField — US-067 accessibility', () => {
  it('shows inline error below the field on invalid submit', async () => {
    const user = userEvent.setup();
    render(<TestForm />);

    await user.click(screen.getByRole('button', { name: 'Invia' }));

    expect(
      await screen.findByText('Il nome utente è obbligatorio'),
    ).toBeInTheDocument();
  });

  it('links invalid field to its error via aria-describedby', async () => {
    const user = userEvent.setup();
    render(<TestForm />);

    await user.click(screen.getByRole('button', { name: 'Invia' }));

    const usernameInput = await screen.findByRole('textbox', { name: 'Nome utente' });
    expect(usernameInput).toHaveAttribute('aria-describedby', 'username-error');

    const errorParagraph = document.getElementById('username-error');
    expect(errorParagraph).toBeInTheDocument();
    expect(errorParagraph).toHaveTextContent('Il nome utente è obbligatorio');
  });

  it('renders error summary at the top of the form after failed submit', async () => {
    const user = userEvent.setup();
    render(<TestForm />);

    await user.click(screen.getByRole('button', { name: 'Invia' }));

    await screen.findByText(/errori nel modulo/);

    const summaryContainer = screen.getByText(/errori nel modulo/).closest('[aria-live="assertive"]')!;
    expect(summaryContainer).toBeInTheDocument();
    expect(summaryContainer).toHaveTextContent('Nome utente');
    expect(summaryContainer).toHaveTextContent('Email');
  });

  it('summary has aria-live="assertive" for screen reader announcement', async () => {
    const user = userEvent.setup();
    render(<TestForm />);

    await user.click(screen.getByRole('button', { name: 'Invia' }));

    await screen.findByText(/errori nel modulo/);
    const summaryContainer = screen.getByText(/errori nel modulo/).closest('[aria-live]');
    expect(summaryContainer).toHaveAttribute('aria-live', 'assertive');
  });

  it('clicking an error link in the summary focuses the corresponding field', async () => {
    const user = userEvent.setup();
    render(<TestForm />);

    await user.click(screen.getByRole('button', { name: 'Invia' }));

    const link = await screen.findByRole('link', { name: /Nome utente/ });
    await user.click(link);

    expect(document.getElementById('username')).toHaveFocus();
  });

  it('has no axe-core accessibility violations', async () => {
    const user = userEvent.setup();
    const { container } = render(<TestForm />);

    await user.click(screen.getByRole('button', { name: 'Invia' }));
    await screen.findByText(/errori nel modulo/);

    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
