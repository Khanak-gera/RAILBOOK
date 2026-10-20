let selectedTrain = null;
let allBookings = [];
let currentFilter = 'ALL';
let currentTicket = null;
let toastTimer = null;

const $ = (id) => document.getElementById(id);

// Makes an element with a class and text. Using textContent keeps user input safe.
function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function toast(text, isError) {
  const t = $('toast');
  t.textContent = text;
  t.className = 'toast ' + (isError ? 'error' : 'ok');
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, 4000);
}

// Sends a request and returns the answer. Throws the server's error message if it fails.
async function api(url, options) {
  const res = await fetch(url, options);
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || 'Something went wrong');
  return data;
}

function post(url, fields) {
  return api(url, { method: 'POST', body: new URLSearchParams(fields) });
}

function cell(row, text, className) {
  const td = row.insertCell();
  if (className) td.appendChild(el('span', className, text));
  else td.textContent = text;
}

// ---------- switching between the 3 pages ----------

function showPage(name) {
  $('pageSearch').hidden = name !== 'search';
  $('pageConfirm').hidden = name !== 'confirm';
  $('pageTickets').hidden = name !== 'tickets';
  $('navSearch').className = name === 'search' ? 'active' : '';
  $('navTickets').className = name === 'search' ? '' : 'active';
  if (name !== 'tickets') $('banner').hidden = true;
  if (name === 'tickets') loadBookings();
  window.scrollTo(0, 0);
}

// ---------- PAGE 1: trains and booking ----------

async function loadTrains(from, to) {
  try {
    const query = from && to
      ? '?from=' + encodeURIComponent(from) + '&to=' + encodeURIComponent(to) : '';
    renderTrains(await api('/api/trains' + query));
  } catch (e) {
    toast(e.message, true);
  }
}

function refreshTrains() {
  return loadTrains($('from').value, $('to').value);
}

function renderTrains(trains) {
  const list = $('trainList');
  list.textContent = '';
  $('resultTitle').textContent = trains.length + (trains.length === 1 ? ' train found' : ' trains found');
  if (trains.length === 0) {
    list.appendChild(el('div', 'empty', 'No trains found for that route. Try "Show all".'));
    return;
  }

  trains.forEach((t) => {
    const card = el('div', 'train-card');

    // left: name, number, route
    const info = el('div');
    const title = el('div', 'train-title');
    title.append(el('strong', '', t.trainName), el('span', 'badge ' + t.type.toLowerCase(), t.type));
    const route = el('div', 'route');
    route.append(el('span', '', t.source), el('span', 'route-line'), el('span', '', t.destination));
    info.append(title, el('div', 'mono muted small', 'Train ' + t.trainNumber), route);

    // middle: seats bar
    const seats = el('div');
    const used = t.totalSeats - t.availableSeats;
    const track = el('div', 'bar-track');
    const fill = el('div', 'bar-fill' + (t.availableSeats === 0 ? ' full' : ''));
    fill.style.width = (t.totalSeats > 0 ? (used / t.totalSeats) * 100 : 0) + '%';
    track.appendChild(fill);
    seats.append(
      el('div', 'seat-text', t.availableSeats > 0
        ? t.availableSeats + ' of ' + t.totalSeats + ' seats left' : 'Train full'),
      track,
      el('div', 'muted small', t.waitingCount + ' on the waiting list')
    );

    // right: fare and button
    const buy = el('div', 'buy');
    const btn = el('button', t.availableSeats > 0 ? '' : 'ghost',
      t.availableSeats > 0 ? 'Book' : 'Join waiting list');
    btn.onclick = () => openBooking(t);
    buy.append(el('div', 'fare', 'Rs.' + t.fare.toFixed(2)), btn);

    card.append(info, seats, buy);
    list.appendChild(card);
  });
}

function openBooking(train) {
  selectedTrain = train;
  $('modalTitle').textContent = 'Book: ' + train.trainName;
  $('modalNote').textContent = train.availableSeats > 0
    ? 'A seat is assigned automatically.'
    : 'This train is full. You will be added to the waiting list.';
  $('pname').value = '';
  $('page').value = '';
  $('modal').hidden = false;
  $('pname').focus();
}

function closeModal() {
  $('modal').hidden = true;
}

async function book() {
  try {
    const t = await post('/api/book', {
      trainNumber: selectedTrain.trainNumber,
      name: $('pname').value,
      age: $('page').value,
      gender: $('pgender').value,
    });
    closeModal();
    refreshTrains();
    $('pnr').value = t.pnr;
    showConfirm(t);
  } catch (e) {
    toast(e.message, true);
  }
}

// ---------- PAGE 2: confirmation ----------

function ticketLine(label, value) {
  const div = el('div', 'ticket-line');
  div.append(el('span', '', label), el('span', '', value));
  return div;
}

function ticketCard(t) {
  const card = el('div', 'ticket');
  const head = el('div', 'ticket-head');
  head.append(el('span', '', 'E-TICKET'), el('span', 'badge ' + t.status.toLowerCase(), t.status));
  const body = el('div', 'ticket-body');
  body.append(
    ticketLine('Passenger', t.name + ', ' + t.age + ', ' + t.gender),
    ticketLine('Train', String(t.trainNumber)),
    ticketLine('Seat', t.seat === 0 ? 'Not assigned yet' : String(t.seat)),
    ticketLine('Fare', 'Rs.' + t.fare.toFixed(2))
  );
  card.append(head, el('div', 'ticket-pnr mono', t.pnr), body);
  return card;
}

function showConfirm(t) {
  currentTicket = t;
  const titles = {
    CONFIRMED: 'Booking confirmed',
    WAITING: "You're on the waiting list",
    CANCELLED: 'Ticket cancelled',
  };
  const subs = {
    CONFIRMED: 'Seat ' + t.seat + ' on train ' + t.trainNumber + ' is reserved for ' + t.name + '.',
    WAITING: 'No seat is free right now. If a confirmed passenger cancels, you move up automatically.',
    CANCELLED: 'This ticket was cancelled and its seat was released.',
  };
  const icons = { CONFIRMED: '\u2713', WAITING: '\u2026', CANCELLED: '\u2715' };

  const head = $('confirmHead');
  head.textContent = '';
  head.append(
    el('div', 'status-icon ' + t.status.toLowerCase(), icons[t.status]),
    el('h1', '', titles[t.status]),
    el('p', 'sub', subs[t.status])
  );

  const box = $('confirmTicket');
  box.textContent = '';
  box.appendChild(ticketCard(t));

  $('cancelThisBtn').hidden = t.status === 'CANCELLED';
  showPage('confirm');
}

async function saveTicket() {
  try {
    const result = await post('/api/save', { pnr: currentTicket.pnr });
    toast(result.message, false);
  } catch (e) {
    toast(e.message, true);
  }
}

// ---------- PAGE 3: my tickets ----------

async function loadBookings() {
  try {
    allBookings = await api('/api/bookings');
    renderStats();
    renderBookings();
  } catch (e) {
    toast(e.message, true);
  }
}

function renderStats() {
  const confirmed = allBookings.filter((t) => t.status === 'CONFIRMED');
  const waiting = allBookings.filter((t) => t.status === 'WAITING');
  const cancelled = allBookings.filter((t) => t.status === 'CANCELLED');
  const revenue = confirmed.reduce((sum, t) => sum + t.fare, 0);

  const cards = [
    ['Total bookings', String(allBookings.length)],
    ['Confirmed', String(confirmed.length)],
    ['Waiting', String(waiting.length)],
    ['Cancelled', String(cancelled.length)],
    ['Revenue (confirmed)', 'Rs.' + revenue.toFixed(2)],
  ];
  const box = $('stats');
  box.textContent = '';
  cards.forEach(([label, value]) => {
    const card = el('div', 'stat');
    card.append(el('div', 'stat-num', value), el('div', 'stat-label', label));
    box.appendChild(card);
  });
}

function renderBookings() {
  const body = $('bookingBody');
  body.textContent = '';
  const rows = currentFilter === 'ALL'
    ? allBookings : allBookings.filter((t) => t.status === currentFilter);

  if (rows.length === 0) {
    const r = body.insertRow();
    const td = r.insertCell();
    td.colSpan = 7;
    td.className = 'empty';
    td.textContent = 'No bookings here yet.';
    return;
  }

  rows.forEach((t) => {
    const row = body.insertRow();
    row.className = 'clickable';
    row.onclick = () => showConfirm(t);
    cell(row, t.pnr, 'mono');
    cell(row, t.name + ', ' + t.age + ', ' + t.gender);
    cell(row, String(t.trainNumber));
    cell(row, t.seat === 0 ? '-' : String(t.seat));
    cell(row, t.status, 'badge ' + t.status.toLowerCase());
    cell(row, 'Rs.' + t.fare.toFixed(2));

    const td = row.insertCell();
    if (t.status !== 'CANCELLED') {
      const btn = el('button', 'danger', 'Cancel');
      btn.onclick = (e) => { e.stopPropagation(); cancelPnr(t.pnr); };
      td.appendChild(btn);
    }
  });
}

// Cancels a ticket. The server promotes the first waiting passenger if there is one.
async function cancelPnr(pnr) {
  if (!pnr) { toast('Enter a PNR first.', true); return false; }
  if (!confirm('Cancel ticket ' + pnr + '?')) return false;
  try {
    const result = await post('/api/cancel', { pnr: pnr });
    const banner = $('banner');
    banner.textContent = result.message;
    banner.hidden = false;
    refreshTrains();
    loadBookings();
    return true;
  } catch (e) {
    toast(e.message, true);
    return false;
  }
}

async function checkTicket() {
  try {
    const t = await api('/api/ticket?pnr=' + encodeURIComponent($('pnr').value.trim()));
    showConfirm(t);
  } catch (e) {
    toast(e.message, true);
  }
}

// ---------- connect everything ----------

$('searchBtn').onclick = refreshTrains;
$('allBtn').onclick = () => loadTrains();
$('from').onkeydown = (e) => { if (e.key === 'Enter') refreshTrains(); };
$('to').onkeydown = (e) => { if (e.key === 'Enter') refreshTrains(); };
$('bookBtn').onclick = book;
$('closeModal').onclick = closeModal;
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeModal(); });

$('saveBtn').onclick = saveTicket;
$('cancelThisBtn').onclick = async () => {
  if (await cancelPnr(currentTicket.pnr)) showPage('tickets');
};
$('anotherBtn').onclick = () => showPage('search');
$('toTicketsBtn').onclick = () => showPage('tickets');

$('checkBtn').onclick = checkTicket;
$('cancelBtn').onclick = () => cancelPnr($('pnr').value.trim());
$('filters').onclick = (e) => {
  const f = e.target.dataset.f;
  if (!f) return;
  currentFilter = f;
  document.querySelectorAll('#filters .chip').forEach((c) => {
    c.classList.toggle('active', c.dataset.f === f);
  });
  renderBookings();
};

$('navSearch').onclick = (e) => { e.preventDefault(); showPage('search'); };
$('navTickets').onclick = (e) => { e.preventDefault(); showPage('tickets'); };
$('logo').onclick = (e) => { e.preventDefault(); showPage('search'); };

loadTrains();