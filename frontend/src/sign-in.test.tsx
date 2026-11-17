import { render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { SignIn } from './sign-in';
import { SessionTransport } from './session-transport';

afterEach(()=>vi.unstubAllGlobals());
it('keeps sign-in available when a concurrent navigation cancels the session read',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(Response.json({enabled:true})));
  const transport=new SessionTransport(vi.fn(),vi.fn().mockRejectedValue(new Error('STALE_SESSION_RESPONSE')));
  render(<MemoryRouter><SignIn transport={transport}/></MemoryRouter>);
  expect((await screen.findByRole('link',{name:'Continue to sign in'})).getAttribute('href')).toBe('/oauth2/authorization/reconrail');
  expect(screen.queryByText(/Sign-in isn’t available/)).toBeNull();
});
