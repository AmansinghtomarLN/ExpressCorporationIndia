#!/usr/bin/env python3
"""Generate src/main/resources/data/india-cities.csv (city,state)."""
from pathlib import Path

CITIES = {
    "Madhya Pradesh": [
        "Agar Malwa", "Alirajpur", "Amarpatan", "Anuppur", "Ashoknagar", "Balaghat",
        "Barwani", "Betul", "Bhind", "Bhopal", "Burhanpur", "Chhatarpur", "Chhindwara",
        "Churhat", "Damoh", "Datia", "Depalpur", "Dewas", "Dhar", "Dindori", "Guna",
        "Gwalior", "Harda", "Hatod", "Indore", "Itarsi", "Jabalpur", "Jhabua", "Katni",
        "Khandwa", "Khargone", "Maihar", "Mandla", "Mandsaur", "Mauganj", "Mhow",
        "Morena", "Nagda", "Narmadapuram", "Narsinghpur", "Neemuch", "Niwari", "Pandhurna",
        "Panna", "Pipariya", "Pithampur", "Raisen", "Rajgarh", "Ratlam", "Rau", "Rewa",
        "Sagar", "Sanawad", "Sanwer", "Satna", "Sehore", "Sendhwa", "Seoni", "Shahdol",
        "Shajapur", "Sheopur", "Shivpuri", "Sidhi", "Singrauli", "Tikamgarh", "Ujjain",
        "Umaria", "Vidisha", "Waidhan",
    ],
    "Chhattisgarh": [
        "Ambikapur", "Balod", "Baloda Bazar", "Bemetara", "Bhilai", "Bijapur", "Bilaspur",
        "Champa", "Dantewada", "Dhamtari", "Durg", "Gariaband", "Gaurela", "Jagdalpur",
        "Janjgir", "Jashpur", "Kanker", "Kawardha", "Kondagaon", "Korba", "Mahasamund",
        "Mungeli", "Narayanpur", "Raigarh", "Raipur", "Rajnandgaon", "Sukma", "Surajpur",
        "Surguja",
    ],
    "Maharashtra": [
        "Ahmednagar", "Akola", "Amravati", "Aurangabad", "Beed", "Bhandara", "Buldhana",
        "Chandrapur", "Dhule", "Gadchiroli", "Gondia", "Hingoli", "Ichalkaranji", "Jalgaon",
        "Jalna", "Kolhapur", "Latur", "Malegaon", "Mumbai", "Nagpur", "Nanded", "Nandurbar",
        "Nashik", "Osmanabad", "Palghar", "Panvel", "Parbhani", "Pune", "Raigad", "Ratnagiri",
        "Sangli", "Satara", "Sindhudurg", "Solapur", "Thane", "Wardha", "Washim", "Yavatmal",
    ],
    "Gujarat": [
        "Ahmedabad", "Amreli", "Anand", "Ankleshwar", "Bharuch", "Bhavnagar", "Bhuj",
        "Botad", "Dahod", "Gandhidham", "Gandhinagar", "Godhra", "Himmatnagar", "Jamnagar",
        "Junagadh", "Mehsana", "Morbi", "Nadiad", "Navsari", "Palanpur", "Patan", "Porbandar",
        "Rajkot", "Surat", "Surendranagar", "Vadodara", "Valsad", "Vapi", "Veraval",
    ],
    "Rajasthan": [
        "Ajmer", "Alwar", "Banswara", "Baran", "Barmer", "Bharatpur", "Bhilwara", "Bikaner",
        "Bundi", "Chittorgarh", "Churu", "Dausa", "Dholpur", "Dungarpur", "Hanumangarh",
        "Jaipur", "Jaisalmer", "Jalore", "Jhalawar", "Jhunjhunu", "Jodhpur", "Karauli",
        "Kota", "Nagaur", "Pali", "Pratapgarh", "Rajsamand", "Sawai Madhopur", "Sikar",
        "Sirohi", "Sri Ganganagar", "Tonk", "Udaipur",
    ],
    "Uttar Pradesh": [
        "Agra", "Aligarh", "Ambedkar Nagar", "Amethi", "Amroha", "Auraiya", "Ayodhya",
        "Azamgarh", "Baghpat", "Bahraich", "Ballia", "Balrampur", "Banda", "Barabanki",
        "Bareilly", "Basti", "Bhadohi", "Bijnor", "Budaun", "Bulandshahr", "Chandauli",
        "Chitrakoot", "Deoria", "Etah", "Etawah", "Farrukhabad", "Fatehpur", "Firozabad",
        "Gautam Buddha Nagar", "Ghaziabad", "Ghazipur", "Gonda", "Gorakhpur", "Hamirpur",
        "Hapur", "Hardoi", "Hathras", "Jalaun", "Jaunpur", "Jhansi", "Kannauj", "Kanpur",
        "Kasganj", "Kaushambi", "Kushinagar", "Lakhimpur Kheri", "Lalitpur", "Lucknow",
        "Maharajganj", "Mahoba", "Mainpuri", "Mathura", "Mau", "Meerut", "Mirzapur",
        "Moradabad", "Muzaffarnagar", "Noida", "Pilibhit", "Prayagraj", "Raebareli",
        "Rampur", "Saharanpur", "Sambhal", "Sant Kabir Nagar", "Shahjahanpur", "Shamli",
        "Shravasti", "Siddharthnagar", "Sitapur", "Sonbhadra", "Sultanpur", "Unnao",
        "Varanasi",
    ],
    "Andhra Pradesh": [
        "Anantapur", "Chittoor", "Eluru", "Guntur", "Kadapa", "Kakinada", "Kurnool",
        "Machilipatnam", "Nellore", "Ongole", "Rajahmundry", "Srikakulam", "Tirupati",
        "Vijayawada", "Visakhapatnam", "Vizianagaram",
    ],
    "Arunachal Pradesh": ["Itanagar", "Naharlagun", "Pasighat", "Tawang", "Ziro"],
    "Assam": [
        "Dibrugarh", "Guwahati", "Jorhat", "Nagaon", "Silchar", "Tezpur", "Tinsukia",
    ],
    "Bihar": [
        "Arrah", "Begusarai", "Bhagalpur", "Bihar Sharif", "Chhapra", "Darbhanga",
        "Gaya", "Hajipur", "Katihar", "Muzaffarpur", "Patna", "Purnia", "Saharsa",
        "Samastipur", "Sasaram",
    ],
    "Goa": ["Mapusa", "Margao", "Panaji", "Vasco da Gama"],
    "Haryana": [
        "Ambala", "Faridabad", "Gurugram", "Hisar", "Karnal", "Panipat", "Rohtak",
        "Sonipat", "Yamunanagar",
    ],
    "Himachal Pradesh": ["Dharamshala", "Mandi", "Shimla", "Solan", "Una"],
    "Jharkhand": [
        "Bokaro", "Deoghar", "Dhanbad", "Hazaribagh", "Jamshedpur", "Ranchi",
    ],
    "Karnataka": [
        "Belagavi", "Ballari", "Bengaluru", "Bhadravati", "Bidar", "Chikkamagaluru",
        "Davanagere", "Gulbarga", "Hassan", "Hubballi", "Mangaluru", "Mysuru",
        "Raichur", "Shivamogga", "Tumakuru", "Udupi",
    ],
    "Kerala": [
        "Alappuzha", "Kannur", "Kochi", "Kollam", "Kottayam", "Kozhikode",
        "Malappuram", "Palakkad", "Thiruvananthapuram", "Thrissur",
    ],
    "Manipur": ["Imphal"],
    "Meghalaya": ["Shillong", "Tura"],
    "Mizoram": ["Aizawl", "Lunglei"],
    "Nagaland": ["Dimapur", "Kohima"],
    "Odisha": [
        "Balasore", "Berhampur", "Bhubaneswar", "Cuttack", "Puri", "Rourkela",
        "Sambalpur",
    ],
    "Punjab": [
        "Amritsar", "Bathinda", "Jalandhar", "Ludhiana", "Mohali", "Patiala",
        "Pathankot",
    ],
    "Sikkim": ["Gangtok", "Namchi"],
    "Tamil Nadu": [
        "Chennai", "Coimbatore", "Erode", "Madurai", "Salem", "Thanjavur",
        "Tiruchirappalli", "Tirunelveli", "Tiruppur", "Vellore",
    ],
    "Telangana": [
        "Hyderabad", "Karimnagar", "Khammam", "Nizamabad", "Warangal",
    ],
    "Tripura": ["Agartala"],
    "Uttarakhand": [
        "Dehradun", "Haldwani", "Haridwar", "Nainital", "Rishikesh", "Roorkee",
    ],
    "West Bengal": [
        "Asansol", "Durgapur", "Howrah", "Kharagpur", "Kolkata", "Siliguri",
    ],
    "Andaman and Nicobar Islands": ["Port Blair"],
    "Chandigarh": ["Chandigarh"],
    "Dadra and Nagar Haveli and Daman and Diu": ["Daman", "Diu", "Silvassa"],
    "Delhi": ["Delhi", "New Delhi"],
    "Jammu and Kashmir": ["Anantnag", "Jammu", "Srinagar"],
    "Ladakh": ["Kargil", "Leh"],
    "Lakshadweep": ["Kavaratti"],
    "Puducherry": ["Puducherry", "Karaikal", "Yanam", "Mahe"],
}

def main():
    root = Path(__file__).resolve().parents[1]
    out = root / "src/main/resources/data/india-cities.csv"
    out.parent.mkdir(parents=True, exist_ok=True)
    rows = []
    seen = set()
    for state, cities in CITIES.items():
        for city in cities:
            key = (city.strip().lower(), state.strip().lower())
            if key in seen:
                continue
            seen.add(key)
            rows.append((city.strip(), state.strip()))
    rows.sort(key=lambda r: (r[1], r[0]))
    with out.open("w", encoding="utf-8", newline="\n") as f:
        f.write("city,state\n")
        for city, state in rows:
            f.write(f"{city},{state}\n")
    print(f"Wrote {len(rows)} cities to {out}")

if __name__ == "__main__":
    main()
