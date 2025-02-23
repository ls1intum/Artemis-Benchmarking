import { Injectable, OnDestroy, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subscriber, Subscription, first } from 'rxjs';
import Stomp, { Client, ConnectionHeaders, Subscription as StompSubscription } from 'webstomp-client';
import { AuthServerProvider } from '../auth/auth-jwt.service';

export class ConnectionState {
  readonly connected: boolean;
  readonly wasEverConnectedBefore: boolean;
  readonly intendedDisconnect: boolean;

  constructor(connected: boolean, wasEverConnectedBefore: boolean, intendedDisconnect: boolean) {
    this.connected = connected;
    this.wasEverConnectedBefore = wasEverConnectedBefore;
    this.intendedDisconnect = intendedDisconnect;
  }
}
@Injectable({
  providedIn: 'root',
})
export class WebsocketService implements OnDestroy {
  private authServerProvider = inject(AuthServerProvider);

  private stompClient?: Client;

  // we store the STOMP subscriptions per channel so that we can unsubscribe in case we are not interested any more
  private stompSubscriptions = new Map<string, StompSubscription>();
  // we store the observables per channel to make sure we can resubscribe them in case of connection issues
  private observables = new Map<string, Observable<any>>();
  // we store the subscribers (represent the components who want to receive messages) per channel so that we can notify them in case a message was received from the server
  private subscribers = new Map<string, Subscriber<any>>();
  // we store the subscription that waits for a connection before subscribing to a channel for the edge case: a component subscribes to a channel, but it already unsubscribes before a connection takes place
  private waitUntilConnectionSubscriptions = new Map<string, Subscription>();

  private alreadyConnectedOnce = false;
  private shouldReconnect = false;
  private readonly connectionStateInternal: BehaviorSubject<ConnectionState>;
  private consecutiveFailedAttempts = 0;
  private connecting = false;
  constructor() {
    this.connectionStateInternal = new BehaviorSubject<ConnectionState>(new ConnectionState(false, false, true));
  }

  private static parseJSON(response: string): any {
    try {
      return JSON.parse(response);
    } catch {
      return response;
    }
  }

  get connectionState(): Observable<ConnectionState> {
    return this.connectionStateInternal.asObservable();
  }

  /**
   * Callback function managing the amount of failed connection attempts to the websocket and the timeout until the next reconnect attempt.
   *
   * Wait 5 seconds before reconnecting in case the connection does not work or the client is disconnected,
   * after  2 failed attempts in row, increase the timeout to 10 seconds,
   * after  4 failed attempts in row, increase the timeout to 20 seconds
   * after  8 failed attempts in row, increase the timeout to 60 seconds
   * after 12 failed attempts in row, increase the timeout to 120 seconds
   * after 16 failed attempts in row, increase the timeout to 300 seconds
   * after 20 failed attempts in row, increase the timeout to 600 seconds
   */
  stompFailureCallback(): void {
    this.connecting = false;
    this.consecutiveFailedAttempts++;
    if (this.connectionStateInternal.getValue().connected) {
      this.connectionStateInternal.next(new ConnectionState(false, this.alreadyConnectedOnce, false));
    }
    if (this.shouldReconnect) {
      let waitUntilReconnectAttempt;
      if (this.consecutiveFailedAttempts > 20) {
        // NOTE: normally a user would reload here anyway
        waitUntilReconnectAttempt = 600;
      } else if (this.consecutiveFailedAttempts > 16) {
        // NOTE: normally a user would reload here anyway
        waitUntilReconnectAttempt = 300;
      } else if (this.consecutiveFailedAttempts > 12) {
        waitUntilReconnectAttempt = 120;
      } else if (this.consecutiveFailedAttempts > 8) {
        waitUntilReconnectAttempt = 60;
      } else if (this.consecutiveFailedAttempts > 4) {
        waitUntilReconnectAttempt = 20;
      } else if (this.consecutiveFailedAttempts > 2) {
        waitUntilReconnectAttempt = 10;
      } else {
        waitUntilReconnectAttempt = 5;
      }
      setTimeout(this.connect.bind(this), waitUntilReconnectAttempt * 1000);
    }
  }

  /**
   * Set up the websocket connection.
   */
  connect(): void {
    if (this.isConnected() || this.connecting) {
      return; // don't connect, if already connected or connecting
    }
    this.connecting = true;
    const authToken = this.authServerProvider.getToken();
    // NOTE: we add 'websocket' twice to use STOMP without SockJS
    const url = `ws://${window.location.host}/websocket/websocket?access_token=${authToken}`;
    const options = {
      heartbeat: { outgoing: 10000, incoming: 10000 },
      debug: false,
      protocols: ['v12.stomp'],
    };
    this.stompClient = Stomp.over(url, options);
    // Note: at the moment, debugging is deactivated to prevent console log statements
    // eslint-disable-next-line @typescript-eslint/no-empty-function
    this.stompClient.debug = () => {};
    const headers = {} as ConnectionHeaders;

    this.stompClient.connect(
      headers,
      () => {
        this.connecting = false;
        if (!this.connectionStateInternal.getValue().connected) {
          this.connectionStateInternal.next(new ConnectionState(true, this.alreadyConnectedOnce, false));
        }
        this.consecutiveFailedAttempts = 0;
        if (this.alreadyConnectedOnce) {
          // (re)connect to all existing channels
          if (this.observables.size !== 0) {
            this.observables.forEach((observable, channel) => this.addSubscription(channel));
          }
        } else {
          this.alreadyConnectedOnce = true;
        }
      },
      this.stompFailureCallback.bind(this),
    );
  }

  public isConnected(): boolean {
    return this.stompClient?.connected ?? false;
  }

  /**
   * Creates a new observable  in case there is no observable for the passed channel yet.
   * Returns the Observable which is invoked when a new message is received
   * @param channel The channel the observable listens on
   */
  receive(channel: string): Observable<any> {
    if (this.observables.size === 0 || !this.observables.has(channel)) {
      this.observables.set(channel, this.createObservable(channel));
    }
    return this.observables.get(channel)!;
  }

  /**
   * Close the connection to the websocket (e.g. due to logout), unsubscribe all observables and set alreadyConnectedOnce to false
   */
  disconnect(): void {
    if (!this.isConnected()) {
      return;
    }
    this.observables.forEach((observable, channel) => this.unsubscribe(channel));
    this.waitUntilConnectionSubscriptions.forEach(subscription => subscription.unsubscribe());
    if (this.stompClient) {
      this.stompClient.disconnect();
      this.stompClient = undefined;
      if (this.connectionStateInternal.getValue().connected || !this.connectionStateInternal.getValue().intendedDisconnect) {
        this.connectionStateInternal.next(new ConnectionState(false, this.alreadyConnectedOnce, true));
      }
    }
    this.alreadyConnectedOnce = false;
  }

  /**
   * On destroy disconnect.
   */
  ngOnDestroy(): void {
    this.disconnect();
  }

  /**
   * Send data through the websocket connection
   * @param path {string} the path for the websocket connection
   * @param data {object} the data to send through the websocket connection
   */
  send(path: string, data: any): void {
    if (this.isConnected()) {
      this.stompClient!.send(path, JSON.stringify(data), {});
    }
  }

  /**
   * Subscribe to a channel: add the channel to the observables and create a STOMP subscription for the channel if this has not been done before
   * @param channel
   */
  subscribe(channel: string): this {
    const subscription = this.connectionState.pipe(first(connectionState => connectionState.connected)).subscribe(() => {
      if (!this.observables.has(channel)) {
        this.observables.set(channel, this.createObservable(channel));
      }
      if (!this.stompSubscriptions.has(channel)) {
        this.addSubscription(channel);
      }
    });
    this.waitUntilConnectionSubscriptions.set(channel, subscription);
    return this;
  }

  /**
   * Unsubscribe a channel.
   * @param channel
   */
  unsubscribe(channel: string): void {
    if (this.stompSubscriptions.has(channel)) {
      this.stompSubscriptions.get(channel)!.unsubscribe();
      this.stompSubscriptions.delete(channel);
      this.observables.delete(channel);
      this.subscribers.delete(channel);
      if (this.waitUntilConnectionSubscriptions.has(channel)) {
        this.waitUntilConnectionSubscriptions.get(channel)!.unsubscribe();
        this.waitUntilConnectionSubscriptions.delete(channel);
      }
    }
  }

  /**
   * Adds a STOMP subscription to the subscribers to receive messages for specific channels
   * @param channel the path (e.g. '/courses/5/exercises/10') that should be subscribed
   */
  private addSubscription(channel: string): void {
    const subscription = this.stompClient!.subscribe(channel, message => {
      // this code is invoked if a new websocket message was received from the server
      // we pass the message to the subscriber (e.g. a component who will be notified and can handle the message)
      if (this.subscribers.has(channel)) {
        this.subscribers.get(channel)!.next(WebsocketService.parseJSON(message.body));
      }
    });
    this.stompSubscriptions.set(channel, subscription);
  }

  /**
   * Create a new observable and store the corresponding subscriber so that we can invoke it when a new message was received
   * @param channel The channel to listen on.
   */
  private createObservable<T>(channel: string): Observable<T> {
    return new Observable((subscriber: Subscriber<T>) => {
      this.subscribers.set(channel, subscriber);
    });
  }
}
