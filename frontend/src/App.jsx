import { useState, useEffect } from 'react';
import './App.css';

function App() {
  // Views: 'home', 'student', 'admin'
  const [currentView, setCurrentView] = useState('home');
  // State for the popup - MUST start as false
  const [showLogin, setShowLogin] = useState(false);

  // Function to handle Admin Login Request
  const handleAdminClick = () => {
    setShowLogin(true); // Only now does the popup show
  };

  // Function when password is correct
  const onAdminLoginSuccess = () => {
    setShowLogin(false);
    setCurrentView('admin');
  };

  return (
    <div className="container">
      {/* 1. LANDING PAGE (Choose your Role) */}
      {currentView === 'home' && (
        <div className="landing-page">
          <h1>Welcome to Hostel Mess</h1>
          <p>Please select your portal to continue</p>
          
          <div className="portal-cards">
            {/* Student Card */}
            <div className="portal-card student-card" onClick={() => setCurrentView('student')}>
              <div className="icon">👨‍🎓</div>
              <h2>Student Portal</h2>
              <p>Vote for food & check daily menu</p>
            </div>

            {/* Admin Card */}
            <div className="portal-card admin-card" onClick={handleAdminClick}>
              <div className="icon">🔐</div>
              <h2>Admin Portal</h2>
              <p>Manage menu & view ratings</p>
            </div>
          </div>
        </div>
      )}

      {/* 2. STUDENT DASHBOARD */}
      {currentView === 'student' && (
        <StudentDashboard onBack={() => setCurrentView('home')} />
      )}

      {/* 3. ADMIN DASHBOARD */}
      {currentView === 'admin' && (
        <AdminPanel onLogout={() => setCurrentView('home')} />
      )}

      {/* LOGIN POPUP - Only shows if showLogin is TRUE */}
      {showLogin && (
        <LoginModal 
          onClose={() => setShowLogin(false)} 
          onLogin={onAdminLoginSuccess} 
        />
      )}
    </div>
  );
}

// --- LOGIN MODAL ---
function LoginModal({ onClose, onLogin }) {
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const checkPassword = () => {
    if (password === "admin123") { 
      onLogin();
    } else {
      setError("Wrong Password! ❌");
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal-box">
        <h2>Admin Verification</h2>
        <p>Enter owner password to access.</p>
        <input 
          type="password" 
          placeholder="Password" 
          value={password} 
          onChange={(e) => setPassword(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && checkPassword()}
        />
        {error && <p className="error-msg">{error}</p>}
        <div className="modal-buttons">
          <button className="btn poor" onClick={onClose}>Cancel</button>
          <button className="btn good" onClick={checkPassword}>Login</button>
        </div>
      </div>
    </div>
  );
}

// --- STUDENT DASHBOARD COMPONENT ---
function StudentDashboard({ onBack }) {
  const [counts, setCounts] = useState({ good: 0, avg: 0, poor: 0 });
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0]);
  const [menu, setMenu] = useState(null);

  const sendFeedback = async (type) => {
    const labels = ["Good 😋", "Average 😐", "Poor 🤢"];
    try { 
      await fetch('http://localhost:8000/submit', { method: 'POST', body: type.toString() }); 
      fetchStats(); 
      alert(`Feedback Submitted: You voted ${labels[type]}`);
    } catch (e) { console.error(e); }
  };

  const fetchStats = async () => {
    try { const res = await fetch('http://localhost:8000/stats'); setCounts(await res.json()); } catch (e) {}
  };

  const fetchMenu = async (date) => {
    try { const res = await fetch(`http://localhost:8000/menu?date=${date}`); setMenu(await res.json()); } catch (e) {}
  };

  useEffect(() => { fetchStats(); const i = setInterval(fetchStats, 2000); return () => clearInterval(i); }, []);
  useEffect(() => { fetchMenu(selectedDate); }, [selectedDate]);

  return (
    <div className="dashboard-view">
      <div className="header-row">
        <h1>Student Dashboard 🎓</h1>
        <button className="btn-outline" onClick={onBack}>⬅ Back to Home</button>
      </div>

      <div className="card">
        <h2>Live Feedback Counter</h2>
        <div className="button-group">
          <button className="btn good" onClick={() => sendFeedback(0)}>Good 😋</button>
          <button className="btn avg" onClick={() => sendFeedback(1)}>Average 😐</button>
          <button className="btn poor" onClick={() => sendFeedback(2)}>Poor 🤢</button>
        </div>
      </div>

      <div className="card menu-card">
        <h2>Daily Menu & Nutrition</h2>
        <div className="date-picker-container">
          <label>Select Date: </label>
          <input type="date" value={selectedDate} onChange={(e) => setSelectedDate(e.target.value)} />
        </div>
        {menu && (
          <div className="menu-grid">
            <MenuCard title="Breakfast 🍳" data={menu.breakfast} />
            <MenuCard title="Lunch 🍛" data={menu.lunch} />
            <MenuCard title="Snacks ☕" data={menu.snacks} />
            <MenuCard title="Dinner 🍲" data={menu.dinner} />
          </div>
        )}
      </div>
    </div>
  );
}

// --- ADMIN DASHBOARD COMPONENT ---
function AdminPanel({ onLogout }) {
  const [counts, setCounts] = useState({ good: 0, avg: 0, poor: 0 });
  const [day, setDay] = useState("MONDAY");
  const [meal, setMeal] = useState("LUNCH");
  const [item, setItem] = useState("");
  const [carbs, setCarbs] = useState("0g");
  const [fat, setFat] = useState("0g");
  const [prot, setProt] = useState("0g");

  useEffect(() => {
    const fetchStats = async () => {
      try { const res = await fetch('http://localhost:8000/stats'); setCounts(await res.json()); } catch (e) {}
    };
    fetchStats();
    const i = setInterval(fetchStats, 2000);
    return () => clearInterval(i);
  }, []);

  const handleUpdate = async (e) => {
    e.preventDefault();
    const payload = `${day}|${meal}|${item}|${carbs}|${fat}|${prot}`;
    try {
      await fetch('http://localhost:8000/admin/update', { method: 'POST', body: payload });
      alert("Menu Updated Successfully! ✅");
      setItem(""); 
    } catch (error) { alert("Failed to update menu ❌"); }
  };

  return (
    <div className="dashboard-view">
      <div className="header-row">
        <h1>Admin Dashboard 🔐</h1>
        <button className="btn-outline logout" onClick={onLogout}>Log Out ➡</button>
      </div>

      <div className="card">
        <h2>Current Mess Ratings</h2>
        <div className="stats-row">
          <div className="stat-box good-box"><span>Good</span><strong>{counts.good}</strong></div>
          <div className="stat-box avg-box"><span>Avg</span><strong>{counts.avg}</strong></div>
          <div className="stat-box poor-box"><span>Poor</span><strong>{counts.poor}</strong></div>
        </div>
      </div>

      <div className="card">
        <h2>Update Menu Item</h2>
        <form className="admin-form" onSubmit={handleUpdate}>
          <div className="form-row">
            <select value={day} onChange={(e) => setDay(e.target.value)}>
              {["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"].map(d => <option key={d} value={d}>{d}</option>)}
            </select>
            <select value={meal} onChange={(e) => setMeal(e.target.value)}>
              <option value="BREAKFAST">Breakfast</option>
              <option value="LUNCH">Lunch</option>
              <option value="SNACKS">Snacks</option>
              <option value="DINNER">Dinner</option>
            </select>
          </div>
          <input type="text" placeholder="Food Item Name" value={item} onChange={(e) => setItem(e.target.value)} required />
          <div className="form-row">
            <input type="text" placeholder="Carbs" value={carbs} onChange={(e) => setCarbs(e.target.value)} required />
            <input type="text" placeholder="Fat" value={fat} onChange={(e) => setFat(e.target.value)} required />
            <input type="text" placeholder="Protein" value={prot} onChange={(e) => setProt(e.target.value)} required />
          </div>
          <button type="submit" className="btn avg">Update Menu</button>
        </form>
      </div>
    </div>
  );
}

function MenuCard({ title, data }) {
  return (
    <div className="menu-item">
      <h3>{title}</h3>
      <p className="food-name">{data.item}</p>
      <div className="nutrition-info">
        <span className="nutri carb">C: {data.carbs}</span>
        <span className="nutri fat">F: {data.fat}</span>
        <span className="nutri prot">P: {data.protein}</span>
      </div>
    </div>
  );
}

export default App;