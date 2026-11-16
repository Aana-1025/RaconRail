import { act, fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { EvidencePlayground, Unavailable } from './shell';

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({ matches:false, addEventListener:vi.fn(), removeEventListener:vi.fn() }));
});
describe('native synthetic shell', () => {
  it('persists only the nonsensitive pause preference across a new scene mount', () => {
    const first=render(<EvidencePlayground/>);
    fireEvent.click(screen.getByRole('button',{name:'Pause motion'}));
    expect(localStorage.getItem('rr.motion.paused')).toBe('true');
    expect(localStorage.length).toBe(1);
    first.unmount();
    const second=render(<EvidencePlayground/>);
    expect(second.container.querySelector('section')?.getAttribute('data-playing')).toBe('false');
    expect(screen.getByRole('button',{name:'Resume motion'})).toBeTruthy();
  });
  it('keeps example status qualified and changes the inspected source', () => {
    render(<EvidencePlayground/>);
    expect(screen.getByText(/no analysis has run/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button',{name:'Application source'}));
    expect(screen.getByRole('heading',{name:'Independent order expectation'})).toBeTruthy();
    expect(screen.getByRole('button',{name:'Application source'}).getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(screen.getByRole('button',{name:/03 Trace/}));
    expect(screen.getByRole('heading',{name:'Every question starts with a source.'})).toBeTruthy();
    fireEvent.click(screen.getByRole('button',{name:'Reset'}));
    expect(screen.getByRole('button',{name:'Provider source'}).getAttribute('aria-pressed')).toBe('true');
  });
  it('honors live OS reduced-motion changes and cleans up the listener', () => {
    let listener: (()=>void)|undefined;
    const media={matches:false,addEventListener:vi.fn((_type,callback)=>{listener=callback;}),removeEventListener:vi.fn()};
    vi.stubGlobal('matchMedia',vi.fn().mockReturnValue(media));
    const {container,unmount}=render(<EvidencePlayground/>);
    expect(container.querySelector('section')?.getAttribute('data-playing')).toBe('true');
    fireEvent.click(screen.getByRole('button',{name:'Pause motion'}));
    expect(container.querySelector('section')?.getAttribute('data-playing')).toBe('false');
    fireEvent.click(screen.getByRole('button',{name:'Resume motion'}));
    media.matches=true;
    act(()=>listener?.());
    expect(container.querySelector('section')?.getAttribute('data-playing')).toBe('false');
    expect((screen.getByRole('button',{name:'Reduced motion'}) as HTMLButtonElement).disabled).toBe(true);
    unmount();
    expect(media.removeEventListener).toHaveBeenCalled();
  });
  it('offers no file, credential or pretend sign-in form on unavailable private routes', () => {
    const {container}=render(<MemoryRouter><Unavailable/></MemoryRouter>);
    expect(screen.getByText(/Private investigations are not available/)).toBeTruthy();
    expect(container.querySelector('input, form')).toBeNull();
    expect(screen.getByRole('link',{name:/Explore the synthetic example/}).getAttribute('href')).toBe('/example');
  });
});
