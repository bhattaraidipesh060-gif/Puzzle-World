// Regenerates the 9 room JSONs so that each room carries a DISTINCT
// puzzle_type and 10 levels of that single minigame (no shared engine).
// The app assets are the source of truth; backend seed.js imports them verbatim.
//
//   puzzle_type -> engine
//   vault_dial      rotary safe combination
//   cipher_wheel    cypher wheel substitution
//   gear_alignment  interlocking gear mesh
//   sliding_tile    15-puzzle sliding tile
//   terminal_hack   binary<->hex + memory path
//   potion_mix      liquid equalisation (jug)
//   coordinate_maze compass maze pathfinding
//   lock_pin        pick-and-tumbler pin sequence
//   neon_simon      simon-says memory lights
const fs = require('fs');
const path = require('path');

const ASSETS_DIR = process.env.ROOMS_ASSETS_DIR
  || path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'rooms');

function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const diff = i => (i <= 1 ? 'Easy' : i <= 4 ? 'Medium' : i <= 7 ? 'Hard' : 'Expert');

function caesar(s, shift) {
  return s.split('').map(c => {
    const u = c.toUpperCase();
    if (u >= 'A' && u <= 'Z') {
      const base = (c === u) ? 65 : 97;
      return String.fromCharCode(base + ((c.charCodeAt(0) - base + shift) % 26 + 26) % 26);
    }
    return c;
  }).join('');
}
const hexToBin = h => h.split('').map(x => parseInt(x, 16).toString(2).padStart(4, '0')).join('');

// ── level generators (return { title, hints, mechanic }) ────────────────
const LEVELS = {
  vault_dial(room, i, rnd) {
    const digits = 2 + Math.floor(i / 3); // 2..4
    const combo = Array.from({ length: digits }, () => Math.floor(rnd() * 10));
    return {
      title: ['Tumble the Dial', 'Two Turners', 'Locked Sound', 'Triple Twist', 'Spin Again', 'The Second Gate', 'Deep Combination', 'Half the Code', 'Near the Core', 'The Master Dial'][i],
      hints: [
        '🔒 Spin each ring until the numbers line up.',
        `🔒 The safe needs a ${digits}-digit code — one ring per digit.`,
        `🔒 The combination is ${JSON.stringify(combo)}.`
      ],
      mechanic: { mechanic_type: 'vault_dial', digits, range: 9, combination: combo, max_attempts: 6 }
    };
  },

  cipher_wheel(room, i, rnd) {
    const phrases = ['HELLO WORLD', 'THE LAB IS OPEN', 'OPEN SESAME', 'CODE BREAKER', 'SECRET KEEPER', 'BROKEN WHEEL', 'HIDDEN MESSAGE', 'FINAL CIPHER', 'NO MORE SECRETS', 'YOU CRACKED IT'];
    const shifts = [3, 5, 7, 9, 11, 4, 6, 8, 10, 13];
    const answer = phrases[i], shift = shifts[i];
    return {
      title: ['Spin the Wheel', 'Rot One', 'Warm Up', 'Second Wheel', 'Half-Turn', 'The Long Shift', 'Wheel of Words', 'Cracked Ring', 'Frequency Fall', 'The Final Decode'][i],
      hints: [
        '🔐 Turn the cypher wheel until the letters snap into place.',
        `🔐 A Caesar shift of ${shift} — watch the letter frequency.`,
        `🔐 The plaintext is "${answer}".`
      ],
      mechanic: { mechanic_type: 'cipher_wheel', cipher_type: 'caesar', cipher_text: caesar(answer, shift), shift, answer, hint: `Rotate each letter back by ${shift}.` }
    };
  },

  gear_alignment(room, i, rnd) {
    const n = 2 + (i % 4); // 2..5 gears
    const gears = Array.from({ length: n }, () => ({ angle: Math.floor(rnd() * 360), target: 0 }));
    return {
      title: ['First Cog', 'Two-Tooth', 'Spin Chain', 'Gear Train', 'Tight Meshing', 'The Escapement', 'Torque Test', 'Clockwork Core', 'Perfect Timing', 'The Mechanism'][i],
      hints: [
        '⚙️ Rotate the gears until every tooth reads "aligned".',
        `⚙️ ${n} gears to line up — tap each gear to rotate it.`,
        '⚙️ All target angles are 0 (pointing straight up).'
      ],
      mechanic: { mechanic_type: 'gear_alignment', gears }
    };
  },

  sliding_tile(room, i, rnd) {
    const size = i <= 5 ? 3 : 4;
    const solve = [...Array(size * size - 1).keys()].map(x => x + 1).concat(0);
    // scramble by moving the blank a fixed number of valid steps (always solvable)
    let tiles = solve.slice(), blank = tiles.length - 1, moves = 20 + i * 5;
    for (let m = 0; m < moves; m++) {
      const r = blank % size, c = Math.floor(blank / size) | 0;
      const opts = [];
      if (r > 0) opts.push(blank - 1);
      if (r < size - 1) opts.push(blank + 1);
      if (c > 0) opts.push(blank - size);
      if (c < size - 1) opts.push(blank + size);
      const t = opts[Math.floor(rnd() * opts.length)];
      tiles[blank] = tiles[t]; tiles[t] = 0; blank = t;
    }
    return {
      title: ['Book Shuffle', 'One Tile', 'Slide Left', 'Reorder the Shelf', 'Three Out', 'Half Scrambled', 'The Study Lock', 'Almost There', 'Great Work', 'The Perfect Order'][i],
      hints: [
        '📚 Slide tiles into the blank space to order them 1..N.',
        `📚 A ${size}×${size} board — the empty square is your mover.`,
        '📚 The solved board reads 1 2 3 … left-to-right, top-to-bottom.'
      ],
      mechanic: { mechanic_type: 'sliding_tile', size, tiles }
    };
  },

  terminal_hack(room, i, rnd) {
    if (i >= 8) {
      const len = 4 + (i - 8); // 4..5
      const nodes = Array.from({ length: len }, () => Math.floor(rnd() * 9));
      return {
        title: ['Boot Sector', 'Root Login', 'Hex Dump', 'Memory Trace', 'Node Path', 'Stack Trace', 'Kernel Panic', 'Trace the Nodes', 'Ghost in the Cache', 'Root Access'][i],
        hints: [
          '💻 Watch the node order flash, then replay it in sequence.',
          `💻 ${len} nodes light up in a path — memorise it.`,
          `💻 The path is ${JSON.stringify(nodes)}.`
        ],
        mechanic: { mechanic_type: 'terminal_hack', mode: 'memory_path', nodes }
      };
    }
    const words = ['A', 'FF', 'AC', '1F', 'CA', 'BEE', 'DAD', 'F00'];
    const answer = words[i];
    return {
      title: ['Boot Sector', 'Root Login', 'Hex Dump', 'Memory Trace', 'Node Path', 'Stack Trace', 'Kernel Panic', 'Byte by Byte', 'Ghost in the Cache', 'Root Access'][i],
      hints: [
        '💻 Convert the binary stream into hex to pass the firewall.',
        '💻 Group the bits into nibbles (4 bits) then read each as hex.',
        `💻 The answer is ${answer}.`
      ],
      mechanic: { mechanic_type: 'terminal_hack', mode: 'bin_to_hex', input: hexToBin(answer), answer }
    };
  },

  potion_mix(room, i, rnd) {
    const configs = [
      { caps: [5, 3], target: 4 },
      { caps: [8, 5, 3], target: 4 },
      { caps: [5, 3], target: 2 },
      { caps: [6, 4], target: 2 },
      { caps: [10, 6], target: 8 },
      { caps: [7, 3], target: 5 },
      { caps: [6, 5], target: 3 },
      { caps: [9, 5], target: 4 },
      { caps: [8, 3], target: 2 },
      { caps: [12, 8, 5], target: 6 }
    ];
    const { caps, target } = configs[i];
    return {
      title: ['First Pour', 'Two Beakers', 'Measure Twice', 'The Alembic', 'Solvent Split', 'Distill', 'The Elixir', 'Exact Measure', 'Transmute', 'The Great Work'][i],
      hints: [
        '⚗️ Pour between beakers until one holds the target volume.',
        '⚗️ You can fill, empty, and pour one beaker into another.',
        `⚗️ Beakers ${JSON.stringify(caps)}, target ${target}.`
      ],
      mechanic: { mechanic_type: 'potion_mix', beakers: caps.map((cap, idx) => ({ id: idx, cap, vol: 0 })), target }
    };
  },

  coordinate_maze(room, i, rnd) {
    const mazes = [
      ['#####', '#S..#', '#.#.#', '#..E#', '#####'],
      ['######', '#S...#', '#.##.#', '#....#', '#.#E##', '######'],
      ['#####', '#S###', '#.##.', '#.#E#', '#...#', '#####'], // 5 cols issue — keep 5/6 wide
      ['#######', '#S....#', '#.#### #'.replace(' ', ''), '#.....#', '###.#.#', '###.#E#', '#######'],
      ['#######', '#S#...#', '#.#.#.#', '#...#.#', '#####.#', '####E.#', '#######'].map(r => r.length === 7 ? r : r),
      ['#######', '#S....#', '###.##.#', '#..#...#', '#.##.#.#', '#....#E#', '#######'],
      ['########', '#S.....#', '#.####.#', '#.#..#.#', '#.#.##.#', '#......#', '####.##E', '########'],
      ['########', '#S#....#', '#.#.##.#', '#.#..#.#', '#.##.#.#', '#....#.#', '###.####', '#####E##', '########'],
      ['#########', '#S...#.##', '#.#.#.#.#', '#...#...#', '#.###.#.#', '#.#...#.#', '#.#.###.#', '#.....#E#', '#########'],
      ['#########', '#S..####.#'.slice(0, 9), '#.##....#', '#.####.#.#', '#....#.#.#', '##.##.#.#', '#.#..#.#.#', '#.##...#E#', '#########'].map(r => r.padEnd(9, '#'))
    ];
    return {
      title: ['Set the Bearing', 'True North', 'Starboard', 'Against the Wind', 'Dead Reckoning', 'The Overland Route', 'Chart the Stones', 'Compass Rose', 'Lost Cartographer', 'The Final Coordinates'][i],
      hints: [
        '🧭 Move from S to E using compass directions.',
        '🧭 # are walls; . are open ground.',
        '🧭 Head toward E, avoiding dead ends.'
      ],
      mechanic: { mechanic_type: 'coordinate_maze', grid: mazes[i] }
    };
  },

  lock_pin(room, i, rnd) {
    const n = 3 + Math.floor(i / 3); // 3..6 pins
    const pins = Array.from({ length: n }, () => 1 + Math.floor(rnd() * 4));
    return {
      title: ['First Tumbler', 'Two Pins', 'Light Tension', 'Set the Pins', 'The Binding Pin', 'Rake It', 'Shear Line', 'Half Picked', 'Master Keyway', 'The Final Pin'][i],
      hints: [
        '🔑 Set each pin in order, left to right, to the correct depth.',
        `🔑 ${n} pins — click them in sequence.`,
        `🔑 Pin depths are ${JSON.stringify(pins)}.`
      ],
      mechanic: { mechanic_type: 'lock_pin', pins, max_attempts: 5 }
    };
  },

  neon_simon(room, i, rnd) {
    const len = 3 + i; // 3..12
    const seq = Array.from({ length: len }, () => Math.floor(rnd() * 4));
    return {
      title: ['Power On', 'Two Blinks', 'Catch the Flash', 'Quarter Round', 'Colour Combo', 'Speed Up', 'The High Score', 'Blink Chain', 'Watch Closely', 'Beat the Machine'][i],
      hints: [
        '🕹️ Watch the colours flash, then replay the sequence.',
        `🕹️ ${len} lights to remember.`,
        `🕹️ The sequence is ${JSON.stringify(seq)}.`
      ],
      mechanic: { mechanic_type: 'neon_simon', colors: 4, sequence: seq, speed_ms: Math.max(900 - i * 40, 420) }
    };
  }
};

const ROOMS = [
  { id: 'room_01_vault', name: 'The First Vault', theme: 'industrial', type: 'vault_dial',
    desc: "An abandoned underground vault. Rusted pipes line the walls. Faint emergency lights flicker overhead. Someone left behind encoded messages and jury-rigged wire puzzles to protect whatever lies deeper inside. Each solved puzzle brings you closer to the vault's core." },
  { id: 'room_02_cipher_lab', name: 'The Cipher Laboratory', theme: 'laboratory', type: 'cipher_wheel',
    desc: 'A mysterious lab filled with encoded messages, cryptic notes, and strange devices.' },
  { id: 'room_03_observatory', name: 'The Clockwork Observatory', theme: 'observatory', type: 'gear_alignment',
    desc: "High above the city, an astronomer's tower hums with the ticking of a hundred brass gears. Constellations are painted on the domed ceiling. Each puzzle is a mechanism of the great celestial clock — solve them all to stop the countdown and release the observatory's secrets." },
  { id: 'room_04_study', name: "The Puzzle Master's Study", theme: 'study', type: 'sliding_tile',
    desc: 'A warm, wood-paneled study filled with ancient puzzle books, carved wooden boxes, and curious mechanical contraptions. The Puzzle Master left their greatest brain-teasers here — solve them to prove your worth.' },
  { id: 'room_05_crypto', name: "The Cryptographer's Cache", theme: 'vault', type: 'terminal_hack',
    desc: 'Deep in a digital-age bunker, a rogue cryptographer left behind a trail of increasingly sophisticated codes. From simple shifts to layered encryptions, each puzzle builds on the last. Crack them all to unlock the cache.' },
  { id: 'room_06_alchemy', name: "The Alchemist's Laboratory", theme: 'alchemy', type: 'potion_mix',
    desc: 'In a candlelit laboratory, a master alchemist sealed away the secrets of transmutation. Solve ciphers, riddles, and locked mechanisms to complete the Great Work — turning base puzzles into pure gold.' },
  { id: 'room_07_cartography', name: "The Cartographer's Vault", theme: 'cartography', type: 'coordinate_maze',
    desc: "A master cartographer hid the world's lost maps behind layers of nautical riddles, coded coordinates, and locked map cases. Chart the course, decode the legends, and unlock the vault of the mapmaker." },
  { id: 'room_08_locksmith', name: "The Locksmith's Forge", theme: 'forge', type: 'lock_pin',
    desc: "A sweltering forge where a master locksmith once crafted unbreakable locks — and the secret patterns that open them. Embers glow in the dark, and every anvil holds a puzzle. Trace the hidden patterns and crack the forged locks to claim the master key." },
  { id: 'room_09_arcade', name: 'The Neon Arcade', theme: 'arcade', type: 'neon_simon',
    desc: 'A retro arcade after closing time, where every cabinet flickers with one last challenge. Colored panels light up in secret sequences, and the high-score screen holds a hidden code. Watch the flashes, remember the order, and beat the machine at its own game.' }
];

let roomCount = 0, levelCount = 0;
for (const room of ROOMS) {
  const rnd = mulberry32(room.id.split('').reduce((a, c) => a + c.charCodeAt(0), 0));
  // linear 1→2→…→10 graph
  const graph = {};
  const levels = [];
  for (let i = 0; i < 10; i++) {
    const lvlId = `${room.id}_${String(i + 1).padStart(2, '0')}`;
    const gen = LEVELS[room.type](room, i, rnd);
    levels.push({ level_id: lvlId, title: gen.title, difficulty: diff(i), hints: gen.hints, mechanic: gen.mechanic });
    graph[lvlId] = i < 9 ? [`${room.id}_${String(i + 2).padStart(2, '0')}`] : [];
  }

  const doc = { room_id: room.id, room_name: room.name, theme: room.theme, description: room.desc, puzzle_type: room.type, room_graph: graph, levels };
  fs.writeFileSync(path.join(ASSETS_DIR, `${room.id}.json`), JSON.stringify(doc, null, 2) + '\n');
  roomCount++; levelCount += levels.length;
}

console.log(`Generated ${roomCount} rooms / ${levelCount} levels -> ${ASSETS_DIR}`);