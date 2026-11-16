import React from 'react';
import ReactDOM from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Shell, Landing, Guide, Example, Unavailable, MissingPage } from './shell';
import './global.css';

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
const router = createBrowserRouter([{ element: <Shell/>, children: [
  { path: '/', element: <Landing/> }, { path: '/guide', element: <Guide/> },
  { path: '/example', element: <Example/> },
  ...['/sign-in', '/cases', '/account', '/closure-status'].map(path => ({path, element: <Unavailable/>})),
  { path: '*', element: <MissingPage/> },
]}]);
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode><QueryClientProvider client={queryClient}><RouterProvider router={router}/></QueryClientProvider></React.StrictMode>,
);
