import React from 'react';
import ReactDOM from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Shell, Landing, Guide, Example, Unavailable, MissingPage } from './shell';
import './global.css';
import { SessionTransport } from './session-transport';
import { SignIn } from './sign-in';

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
const sessionTransport = new SessionTransport(()=>{queryClient.clear();});
sessionTransport.watch();
const router = createBrowserRouter([{ element: <Shell/>, children: [
  { path: '/', element: <Landing/> }, { path: '/guide', element: <Guide/> },
  { path: '/example', element: <Example/> },
  { path: '/sign-in', element: <SignIn transport={sessionTransport}/> },
  ...['/cases', '/account', '/closure-status'].map(path => ({path, element: <Unavailable/>})),
  { path: '*', element: <MissingPage/> },
]}]);
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode><QueryClientProvider client={queryClient}><RouterProvider router={router}/></QueryClientProvider></React.StrictMode>,
);
